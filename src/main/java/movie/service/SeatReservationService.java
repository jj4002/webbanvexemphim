package movie.service;

import movie.entity.GheEntity;
import movie.entity.VeEntity;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeatReservationService {

    private final SessionFactory sessionFactory;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public SeatReservationService(SessionFactory sessionFactory, SimpMessagingTemplate messagingTemplate) {
        this.sessionFactory = sessionFactory;
        this.messagingTemplate = messagingTemplate;
    }

    @Transactional
 // ĐÚNG: Không set maKhachHang, set donHang = null
    public void reserveSeat(String maVe, String maSuatChieu, String maGhe, BigDecimal giaVe) {
        try {
            Session dbSession = sessionFactory.getCurrentSession();
            VeEntity ve = new VeEntity();
            ve.setMaVe(maVe);
            ve.setMaSuatChieu(maSuatChieu);
            ve.setMaGhe(maGhe);
            ve.setGiaVe(giaVe);
            ve.setNgayMua(new Date());
            ve.setDonHang(null);  // Vé tạm, chưa có đơn hàng
            dbSession.save(ve);
            System.out.println("Reserved seat: MaVe=" + maVe + ", MaGhe=" + maGhe);
        } catch (Exception e) {
            System.err.println("Error reserving seat: " + e.getMessage());
            throw e;
        }
    }


    @Scheduled(fixedRate = 60000) // Mỗi 1 phút
    @Transactional
    public void releaseExpiredSeats() {
        Session dbSession = sessionFactory.getCurrentSession();
        try {
            Date fiveMinutesAgo = new Date(System.currentTimeMillis() - 5 * 60 * 1000);
            
            // *** XÓA vé tạm giữ quá 5 phút (không cần filter maKhachHang) ***
            String hql = "DELETE FROM VeEntity v WHERE v.donHang IS NULL " +
                         "AND v.ngayMua < :fiveMinutesAgo";
            
            Query query = dbSession.createQuery(hql);
            query.setParameter("fiveMinutesAgo", fiveMinutesAgo);
            
            int deletedCount = query.executeUpdate();
            
            if (deletedCount > 0) {
                System.out.println("✓ Released " + deletedCount + " expired seat reservations");
                messagingTemplate.convertAndSend("/topic/expired-seats", deletedCount);
            }
        } catch (Exception e) {
            System.err.println("Error releasing expired seats: " + e.getMessage());
            e.printStackTrace();
        }
    }



    @Transactional
    public void updateSeatReservation(String maSuatChieu, List<String> newSeats, String maKhachHang, BigDecimal giaVe) {
        try {
            Session dbSession = sessionFactory.getCurrentSession();
            
         // ĐÚNG: Xóa vé tạm hết hạn theo maSuatChieu
            Date fiveMinutesAgo = new Date(System.currentTimeMillis() - 5 * 60 * 1000);
            Query deleteQuery = dbSession.createQuery(
                "DELETE FROM VeEntity v WHERE v.maSuatChieu = :maSuatChieu " +
                "AND v.donHang IS NULL " +
                "AND v.ngayMua < :fiveMinutesAgo");
            deleteQuery.setParameter("maSuatChieu", maSuatChieu);
            deleteQuery.setParameter("fiveMinutesAgo", fiveMinutesAgo);

            int deletedCount = deleteQuery.executeUpdate();
            System.out.println("Deleted " + deletedCount + " old reservations");

            List<Map<String, String>> seatInfoList = new ArrayList<>();
            for (String seatId : newSeats) {
                // *** SỬA: Query theo display seat ID ***
                // Giả sử seatId = "A1" (display), cần tìm ghế có TenHangDisplay='A' và số '1'
                String tenHang = seatId.substring(0, 1);  // A
                String soGhe = seatId.substring(1);       // 1
                
                Query gheQuery = dbSession.createSQLQuery(
                	    "SELECT g.MaGhe FROM Ghe g " +
                	    "JOIN V_GheUserView v ON g.MaGhe = v.MaGhe " +
                	    "WHERE v.TenHangDisplay = :tenHang " +
                	    "AND v.SoGheDisplay = :soGhe " +
                	    "AND g.MaPhongChieu IN (" +
                	    "  SELECT sc.MaPhongChieu FROM SuatChieu sc WHERE sc.MaSuatChieu = :maSuatChieu" +
                	    ")");
                	gheQuery.setParameter("tenHang", tenHang);
                	gheQuery.setParameter("soGhe", soGhe);  // Truyền "1" (chỉ số)
                	gheQuery.setParameter("maSuatChieu", maSuatChieu);
                
                String maGhe = (String) gheQuery.uniqueResult();
                if (maGhe != null) {
                    String maVe = "VE" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
                    reserveSeat(maVe, maSuatChieu, maGhe, giaVe);
                    
                    Map<String, String> seatInfo = new HashMap<>();
                    seatInfo.put("seatId", seatId);  // Display ID (A1, B2...)
                    seatInfo.put("maKhachHang", maKhachHang);
                    seatInfoList.add(seatInfo);
                } else {
                    System.err.println("Seat not found: " + seatId);
                }
            }

            messagingTemplate.convertAndSend("/topic/seats/" + maSuatChieu, seatInfoList);
        } catch (Exception e) {
            System.err.println("Error updating seat reservation: " + e.getMessage());
            throw e;
        }
    }

}