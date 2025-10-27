package movie.controller;

import movie.entity.*;
import movie.model.*;
import movie.config.MomoConfig;
import movie.service.SeatReservationService;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


@Controller
@RequestMapping("/booking")
public class BookingController {

    @Autowired
    private SessionFactory sessionFactory;

    @Autowired
    private SeatReservationService seatReservationService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MomoConfig momoConfig;
    
    /**
     * Hủy vé đã thanh toán
     * - Điều kiện: Trước giờ chiếu >= 2 ngày
     * - Hoàn tiền: 90% (phí hủy 10%)
     * - Hoàn điểm đã sử dụng
     */
    @Transactional
    @RequestMapping(value = "/cancel-order", method = RequestMethod.POST)
    public String cancelPaidOrder(
            @RequestParam("maDonHang") String maDonHang,
            HttpSession httpSession,
            RedirectAttributes redirectAttributes) {
        
        KhachHangModel loggedInUser = (KhachHangModel) httpSession.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập");
            return "redirect:/auth/login";
        }

        Session session = sessionFactory.getCurrentSession();
        try {
            System.out.println("=== CANCEL ORDER: " + maDonHang + " ===");
            
            // 1. Lấy đơn hàng
            DonHangEntity donHang = (DonHangEntity) session.get(DonHangEntity.class, maDonHang);
            
            if (donHang == null) {
                redirectAttributes.addFlashAttribute("error", "Đơn hàng không tồn tại");
                return "redirect:/user/profile";
            }
            
            if (!donHang.getMaKhachHang().equals(loggedInUser.getMaKhachHang())) {
                redirectAttributes.addFlashAttribute("error", "Đơn hàng không thuộc về bạn");
                return "redirect:/user/profile";
            }
            
            if (!"Đã thanh toán".equals(donHang.getTrangThaiDonHang())) {
                redirectAttributes.addFlashAttribute("error", "Chỉ có thể hủy đơn hàng đã thanh toán");
                return "redirect:/user/profile";
            }

            // *** FIX: Dùng Native SQL thay vì HQL ***
            String sql = "SELECT MIN(sc.NgayGioChieu) " +
                         "FROM Ve v " +
                         "INNER JOIN SuatChieu sc ON v.MaSuatChieu = sc.MaSuatChieu " +
                         "WHERE v.MaDonHang = :maDonHang";

            Query veQuery = session.createSQLQuery(sql);
            veQuery.setParameter("maDonHang", maDonHang);
            Object result = veQuery.uniqueResult();

            Date ngayGioChieu = null;
            if (result != null) {
                if (result instanceof java.sql.Timestamp) {
                    ngayGioChieu = new Date(((java.sql.Timestamp) result).getTime());
                } else if (result instanceof Date) {
                    ngayGioChieu = (Date) result;
                }
            }
            
            if (ngayGioChieu == null) {
                redirectAttributes.addFlashAttribute("error", "Không tìm thấy thông tin suất chiếu");
                return "redirect:/user/profile";
            }

            // 3. Kiểm tra điều kiện: >= 2 ngày trước giờ chiếu
            long twoDaysInMillis = 2L * 24 * 60 * 60 * 1000;
            long timeUntilShow = ngayGioChieu.getTime() - System.currentTimeMillis();
            
            System.out.println("Giờ chiếu: " + ngayGioChieu);
            System.out.println("Thời gian còn lại: " + (timeUntilShow / (24 * 60 * 60 * 1000)) + " ngày");
            
            if (timeUntilShow < twoDaysInMillis) {
                redirectAttributes.addFlashAttribute("error", 
                    "Chỉ được hủy vé trước giờ chiếu ít nhất 2 ngày. " +
                    "Giờ chiếu: " + new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(ngayGioChieu));
                return "redirect:/user/profile";
            }

            // 4. Tính tiền hoàn lại (90% - phí hủy 10%)
            BigDecimal refundAmount = donHang.getTongTien()
                .multiply(new BigDecimal("0.9"))
                .setScale(0, BigDecimal.ROUND_HALF_UP);
            BigDecimal cancelFee = donHang.getTongTien()
                .multiply(new BigDecimal("0.1"))
                .setScale(0, BigDecimal.ROUND_HALF_UP);

            // 5. Hoàn điểm đã sử dụng (nếu có)
            int refundedPoints = 0;
            if (donHang.getDiemSuDung() != null && donHang.getDiemSuDung() > 0) {
                KhachHangEntity khachHang = (KhachHangEntity) session.get(
                    KhachHangEntity.class, loggedInUser.getMaKhachHang());
                
                int currentPoints = khachHang.getTongDiem();
                khachHang.setTongDiem(currentPoints + donHang.getDiemSuDung());
                session.update(khachHang);
                
                refundedPoints = donHang.getDiemSuDung();
                
                // Update session
                loggedInUser.setTongDiem(khachHang.getTongDiem());
                httpSession.setAttribute("loggedInUser", loggedInUser);
                
                System.out.println("Hoàn " + refundedPoints + " điểm");
            }

            // 6. Cập nhật trạng thái đơn hàng
            donHang.setTrangThaiDonHang("Đã hủy");
            session.update(donHang);

            // 7. Xóa vé (giải phóng ghế)
            Query deleteVeQuery = session.createQuery(
                "DELETE FROM VeEntity v WHERE v.donHang.maDonHang = :maDonHang");
            deleteVeQuery.setParameter("maDonHang", maDonHang);
            int deletedCount = deleteVeQuery.executeUpdate();
            System.out.println("Đã xóa " + deletedCount + " vé");

            // 8. Tạo bản ghi hoàn tiền
            ThanhToanEntity refund = new ThanhToanEntity();
            String timestamp = String.valueOf(System.currentTimeMillis());
            refund.setMaThanhToan("RF" + timestamp.substring(timestamp.length() - 8));
            refund.setDonHang(donHang);
            refund.setPhuongThuc("Hoàn tiền (Hủy vé)");
            refund.setSoTien(refundAmount);
            refund.setNgayThanhToan(new Date());
            refund.setTrangThai("Success");
            session.save(refund);

            System.out.println("✓ Hủy vé thành công");
            
            redirectAttributes.addFlashAttribute("success", 
                String.format("Hủy vé thành công!\n" +
                    "• Số tiền hoàn lại: %,dđ (90%%)\n" +
                    "• Phí hủy: %,dđ (10%%)\n" +
                    "• Điểm hoàn lại: %d điểm\n" +
                    "Tiền sẽ được hoàn trong 3-5 ngày làm việc.",
                    refundAmount.intValue(),
                    cancelFee.intValue(),
                    refundedPoints));
            
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }

        return "redirect:/user/profile";
    }




    @Transactional
    @RequestMapping(value = "/select-seats", method = RequestMethod.GET)
    public String showSeatSelection(
            @RequestParam(value = "maPhim", required = true) String maPhim,
            @RequestParam(value = "maSuatChieu", required = true) String maSuatChieu,
            HttpSession session,
            Model model) {
        try {
            KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
            if (loggedInUser == null) {
                session.setAttribute("redirectMaPhim", maPhim);
                session.setAttribute("redirectMaSuatChieu", maSuatChieu);
                model.addAttribute("error", "Vui lòng đăng nhập để đặt vé");
                return "redirect:/auth/login";
            }

            Session dbSession = sessionFactory.getCurrentSession();
            
            // Kiểm tra giao dịch đang chờ (dựa trên session)
            String currentReservation = (String) session.getAttribute("maSuatChieu");
            if (currentReservation != null && !currentReservation.equals(maSuatChieu)) {
                Long reservationTime = (Long) session.getAttribute("reservationStartTime");
                if (reservationTime != null) {
                    long elapsed = System.currentTimeMillis() - reservationTime;
                    if (elapsed < 5 * 60 * 1000) {
                        model.addAttribute("error", "Bạn đang có giao dịch chưa hoàn thành. " +
                            "Vui lòng hoàn tất thanh toán hoặc đợi hết thời gian giữ ghế (5 phút).");
                        model.addAttribute("pendingSuatChieu", currentReservation);
                        return "user/pending_transaction_warning";
                    }
                }
            }

            // Query phim
            Query phimQuery = dbSession.createQuery(
                    "FROM PhimEntity p LEFT JOIN FETCH p.theLoais LEFT JOIN FETCH p.dienViens WHERE p.maPhim = :maPhim");
            phimQuery.setParameter("maPhim", maPhim);
            PhimEntity phimEntity = (PhimEntity) phimQuery.uniqueResult();
            if (phimEntity == null) {
                model.addAttribute("error", "Phim không tồn tại");
                return "user/book-ticket";
            }

            // Query suất chiếu
            Query suatChieuQuery = dbSession.createQuery(
                    "SELECT sc, sc.phongChieu, sc.phongChieu.rapChieu FROM SuatChieuEntity sc WHERE sc.maSuatChieu = :maSuatChieu");
            suatChieuQuery.setParameter("maSuatChieu", maSuatChieu);
            Object[] suatChieuResult = (Object[]) suatChieuQuery.uniqueResult();
            if (suatChieuResult == null) {
                model.addAttribute("error", "Suất chiếu không tồn tại");
                return "user/book-ticket";
            }

            SuatChieuEntity suatChieuEntity = (SuatChieuEntity) suatChieuResult[0];
            PhongChieuEntity phongChieu = (PhongChieuEntity) suatChieuResult[1];
            RapChieuEntity rapChieu = (RapChieuEntity) suatChieuResult[2];

            // Query ghế từ VIEW
            String sql = "SELECT " +
                    "g.MaGhe, " +                              // 0
                    "g.SoGhe AS SoGhePhysical, " +             // 1
                    "g.TenHang AS TenHangPhysical, " +         // 2
                    "g.MaLoaiGhe, " +                          // 3
                    "g.MaPhongChieu, " +                       // 4
                    "v.SoGheDisplay, " +                       // 5
                    "v.TenHangDisplay, " +                     // 6
                    "lg.MaLoaiGhe AS LgMaLoaiGhe, " +          // 7
                    "CAST(lg.TenLoaiGhe AS VARCHAR(50)) AS TenLoaiGhe, " + // 8
                    "lg.HeSoGia, " +                           // 9
                    "lg.MauGhe, " +                            // 10
                    "lg.SoCho " +                              // 11
                    "FROM Ghe g " +
                    "LEFT JOIN V_GheUserView v ON g.MaGhe = v.MaGhe " +
                    "LEFT JOIN LoaiGhe lg ON g.MaLoaiGhe = lg.MaLoaiGhe " +
                    "WHERE g.MaPhongChieu = (SELECT MaPhongChieu FROM SuatChieu WHERE MaSuatChieu = :maSuatChieu)";
       
            Query gheQuery = dbSession.createSQLQuery(sql);
            gheQuery.setParameter("maSuatChieu", maSuatChieu);
            List<Object[]> results = gheQuery.list();

            // Map ghế
            List<GheEntity> gheList = new ArrayList<>();
            Set<String> uniqueRows = new TreeSet<>();
	       
            for (Object[] row : results) {
                GheEntity ghe = new GheEntity();
                ghe.setMaGhe((String) row[0]);
                
                // *** MAP PHYSICAL (từ bảng Ghe) ***
                String soGhePhysical = row[1] != null ? String.valueOf(row[1]).trim() : null;
                String tenHangPhysical = row[2] != null ? String.valueOf(row[2]).trim() : null;
                
                ghe.setSoGhe(soGhePhysical);      // Set physical
                ghe.setTenHang(tenHangPhysical);  // Set physical
                
                // *** MAP DISPLAY (từ VIEW) ***
                String soGheDisplay = row[5] != null ? String.valueOf(row[5]).trim() : soGhePhysical;
                String tenHangDisplay = row[6] != null ? String.valueOf(row[6]).trim() : tenHangPhysical;
                
                // *** KIỂM TRA NULL ***
                if (soGheDisplay == null || tenHangDisplay == null || 
                    soGheDisplay.isEmpty() || tenHangDisplay.isEmpty()) {
                    System.err.println("⚠ SKIP NULL: MaGhe=" + row[0] + 
                                      ", Physical=" + tenHangPhysical + soGhePhysical +
                                      ", Display=" + tenHangDisplay + soGheDisplay);
                    continue;
                }
                
                // *** BỎ QUA HÀNG Q ***
                if ("Q".equalsIgnoreCase(tenHangDisplay)) {
                    System.out.println("⚠ SKIP hàng Q: " + tenHangDisplay + soGheDisplay);
                    continue;
                }
                
                ghe.setSoGheAdmin(soGheDisplay);      // Set display
                ghe.setTenHangAdmin(tenHangDisplay);  // Set display
                
                uniqueRows.add(tenHangDisplay);
                
                // Map loại ghế
                LoaiGheEntity loaiGhe = new LoaiGheEntity();
                loaiGhe.setMaLoaiGhe((String) row[7]);
                loaiGhe.setTenLoaiGhe((String) row[8]);
                loaiGhe.setHeSoGia(row[9] != null ? ((Number) row[9]).doubleValue() : 1.0);
                loaiGhe.setMauGhe((String) row[10]);
                loaiGhe.setSoCho(row[11] != null ? ((Number) row[11]).intValue() : 1);
                ghe.setLoaiGhe(loaiGhe);
                
                gheList.add(ghe);
            }
            
            // *** THÊM: Sắp xếp ghế theo hàng và số ***
            gheList.sort((g1, g2) -> {
                // So sánh theo hàng trước
                int hangCompare = g1.getTenHangAdmin().compareTo(g2.getTenHangAdmin());
                if (hangCompare != 0) {
                    return hangCompare;
                }
                // Nếu cùng hàng, so sánh theo số (chuyển thành int)
                try {
                    int soGhe1 = Integer.parseInt(g1.getSoGheAdmin());
                    int soGhe2 = Integer.parseInt(g2.getSoGheAdmin());
                    return Integer.compare(soGhe1, soGhe2);
                } catch (NumberFormatException e) {
                    // Fallback: so sánh string nếu không parse được
                    return g1.getSoGheAdmin().compareTo(g2.getSoGheAdmin());
                }
            });
            

            System.out.println("=== DEBUG: Final Stats ===");
            System.out.println("Total seats after filter: " + gheList.size());
            System.out.println("Row labels: " + uniqueRows);


            // Query loại ghế
            Query loaiGheQuery = dbSession.createQuery(
                    "SELECT DISTINCT g.loaiGhe FROM GheEntity g WHERE g.phongChieu.maPhongChieu = :maPhongChieu");
            loaiGheQuery.setParameter("maPhongChieu", phongChieu.getMaPhongChieu());
            List<LoaiGheEntity> loaiGheList = loaiGheQuery.list();
            List<LoaiGheModel> loaiGheModels = new ArrayList<>();
            for (LoaiGheEntity entity : loaiGheList) {
                loaiGheModels.add(new LoaiGheModel(entity));
            }

            // Query vé đã đặt
            Query veQuery = dbSession.createQuery(
                "FROM VeEntity v WHERE v.maSuatChieu = :maSuatChieu");
            veQuery.setParameter("maSuatChieu", maSuatChieu);
            List<VeEntity> veList = veQuery.list();

            Date fiveMinutesAgo = new Date(System.currentTimeMillis() - 5 * 60 * 1000);
            Set<String> reservedSeats = new HashSet<>();
            Set<String> paidSeats = new HashSet<>();
            Map<String, Long> seatReservationTimes = new HashMap<>();

            for (VeEntity ve : veList) {
                for (GheEntity ghe : gheList) {
                    if (ghe.getMaGhe().equals(ve.getMaGhe())) {
                        String seatId = ghe.getTenHangAdmin() + ghe.getSoGheAdmin();
                        
                        if (ve.getDonHang() == null && ve.getNgayMua().after(fiveMinutesAgo)) {
                            // Vé tạm giữ
                            reservedSeats.add(seatId);
                            seatReservationTimes.put(seatId, ve.getNgayMua().getTime());
                        } else if (ve.getDonHang() != null) {
                            // Vé đã thanh toán
                            paidSeats.add(seatId);
                        }
                        break;
                    }
                }
            }

            // Lấy ghế đã chọn từ session
            String selectedSeats = (String) session.getAttribute("selectedSeats");
            if (selectedSeats != null && !selectedSeats.isEmpty()) {
                model.addAttribute("selectedSeats", Arrays.asList(selectedSeats.split(",")));
            } else {
                model.addAttribute("selectedSeats", new ArrayList<String>());
            }

            // Tính số cột và hàng
            int maxCot = 0;
            for (GheEntity ghe : gheList) {
                uniqueRows.add(ghe.getTenHangAdmin());
                try {
                    int soGheAsInt = Integer.parseInt(ghe.getSoGhe());
                    maxCot = Math.max(maxCot, soGheAsInt);
                } catch (NumberFormatException e) {
                    System.err.println("Không thể chuyển soGhe thành số: " + ghe.getSoGhe());
                }
            }
            List<String> rowLabels = new ArrayList<>(uniqueRows);
            
            System.out.println("=== DEBUG: Row Labels ===");
            System.out.println("Row labels: " + rowLabels);  // ← In ra kiểm tra
            
            model.addAttribute("rowLabels", rowLabels);
            model.addAttribute("gheList", gheList);

            // Set attributes
            model.addAttribute("phim", new PhimModel(phimEntity));
            model.addAttribute("suatChieu", new SuatChieuModel(suatChieuEntity));
            model.addAttribute("rapChieu", new RapChieuModel(rapChieu));
            model.addAttribute("gheList", gheList);
            model.addAttribute("rowLabels", rowLabels);
            model.addAttribute("soCot", maxCot);
            model.addAttribute("reservedSeats", reservedSeats);
            model.addAttribute("paidSeats", paidSeats);
            model.addAttribute("seatReservationTimes", seatReservationTimes);
            model.addAttribute("loaiGheList", loaiGheModels);

            session.setAttribute("maPhim", maPhim);
            session.setAttribute("maSuatChieu", maSuatChieu);

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Lỗi khi tải trang chọn ghế: " + e.getMessage());
            return "user/book-ticket";
        }
        return "user/book-ticket";
    }


    @Transactional
    @RequestMapping(value = "/reserve-seats", method = RequestMethod.POST)
    public String reserveSeats(
            @RequestParam("maPhim") String maPhim,
            @RequestParam("maSuatChieu") String maSuatChieu,
            @RequestParam("selectedSeats") String selectedSeats,
            HttpSession session,
            Model model) {
        try {
            KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
            if (loggedInUser == null) {
                session.setAttribute("redirectMaPhim", maPhim);
                session.setAttribute("redirectMaSuatChieu", maSuatChieu);
                model.addAttribute("error", "Vui lòng đăng nhập để đặt vé");
                return "redirect:/auth/login";
            }

            if (selectedSeats == null || selectedSeats.isEmpty()) {
                model.addAttribute("error", "Vui lòng chọn ít nhất một ghế");
                return showSeatSelection(maPhim, maSuatChieu, session, model);
            }

            Session dbSession = sessionFactory.getCurrentSession();
            
            // Xóa vé tạm hết hạn (> 5 phút)
            Date fiveMinutesAgo = new Date(System.currentTimeMillis() - 5 * 60 * 1000);
            String deletePendingHQL = "DELETE FROM VeEntity v WHERE v.maSuatChieu = :maSuatChieu " +
                    "AND v.donHang IS NULL " +
                    "AND v.ngayMua < :fiveMinutesAgo";
            Query deleteQuery = dbSession.createQuery(deletePendingHQL);
            deleteQuery.setParameter("maSuatChieu", maSuatChieu);
            deleteQuery.setParameter("fiveMinutesAgo", fiveMinutesAgo);
            int deletedCount = deleteQuery.executeUpdate();
            
            if (deletedCount > 0) {
                System.out.println("✓ Đã xóa " + deletedCount + " vé hết hạn");
            }

            // Query phim
            Query phimQuery = dbSession.createQuery("FROM PhimEntity p WHERE p.maPhim = :maPhim");
            phimQuery.setParameter("maPhim", maPhim);
            PhimEntity phimEntity = (PhimEntity) phimQuery.uniqueResult();
            if (phimEntity == null) {
                model.addAttribute("error", "Phim không tồn tại");
                return "user/book-ticket";
            }

            // Query suất chiếu
            Query suatChieuQuery = dbSession.createQuery("FROM SuatChieuEntity sc WHERE sc.maSuatChieu = :maSuatChieu");
            suatChieuQuery.setParameter("maSuatChieu", maSuatChieu);
            SuatChieuEntity suatChieuEntity = (SuatChieuEntity) suatChieuQuery.uniqueResult();
            if (suatChieuEntity == null) {
                model.addAttribute("error", "Suất chiếu không tồn tại");
                return "user/book-ticket";
            }

            List<String> selectedSeatList = Arrays.asList(selectedSeats.split(","));

            // Query ghế từ VIEW
            String sql = "SELECT " +
                         "g.MaGhe, g.SoGhe, g.TenHang, g.MaLoaiGhe, g.MaPhongChieu, " +
                         "v.SoGheDisplay, v.TenHangDisplay, " +
                         "lg.MaLoaiGhe AS LgMaLoaiGhe, CAST(lg.TenLoaiGhe AS VARCHAR(50)) AS TenLoaiGhe, " +
                         "lg.HeSoGia, lg.MauGhe, lg.SoCho " +
                         "FROM Ghe g " +
                         "LEFT JOIN V_GheUserView v ON g.MaGhe = v.MaGhe " +
                         "LEFT JOIN LoaiGhe lg ON g.MaLoaiGhe = lg.MaLoaiGhe " +
                         "WHERE g.MaPhongChieu = :maPhongChieu " +
                         "ORDER BY v.ThuTuHang, v.SoGheDisplay";

            Query gheQuery = dbSession.createSQLQuery(sql);
            gheQuery.setParameter("maPhongChieu", suatChieuEntity.getPhongChieu().getMaPhongChieu());
            List<Object[]> results = gheQuery.list();

            // Map ghế
            List<GheEntity> gheList = new ArrayList<>();
            for (Object[] row : results) {
                GheEntity ghe = new GheEntity();
                ghe.setMaGhe((String) row[0]);
                ghe.setSoGhe((String) row[1]);
                ghe.setTenHang((String) row[2]);
                ghe.setSoGheAdmin((String) row[5]);
                ghe.setTenHangAdmin((String) row[6]);
                
                if (row[7] != null) {
                    LoaiGheEntity loaiGhe = new LoaiGheEntity();
                    loaiGhe.setMaLoaiGhe((String) row[7]);
                    loaiGhe.setTenLoaiGhe((String) row[8]);
                    loaiGhe.setHeSoGia(((Number) row[9]).doubleValue());
                    loaiGhe.setMauGhe((String) row[10]);
                    loaiGhe.setSoCho(((Number) row[11]).intValue());
                    ghe.setLoaiGhe(loaiGhe);
                }
                
                gheList.add(ghe);
            }

            // Tính số lượng ghế theo loại
            Map<String, Integer> seatCountByType = new HashMap<>();
            for (String seatId : selectedSeatList) {
                for (GheEntity ghe : gheList) {
                    if ((ghe.getTenHangAdmin() + ghe.getSoGheAdmin()).equals(seatId.trim())) {
                        String tenLoaiGhe = ghe.getLoaiGhe().getTenLoaiGhe();
                        seatCountByType.put(tenLoaiGhe, seatCountByType.getOrDefault(tenLoaiGhe, 0) + 1);
                        break;
                    }
                }
            }

            // Tính giá vé
            BigDecimal totalVe = BigDecimal.ZERO;
            Map<String, BigDecimal> vePrices = new HashMap<>();

            for (String seatId : selectedSeatList) {
                for (GheEntity ghe : gheList) {
                    String displayId = ghe.getTenHangAdmin() + ghe.getSoGheAdmin();
                    
                    if (displayId != null && displayId.equals(seatId.trim())) {
                        double heSoGia = ghe.getLoaiGhe().getHeSoGia();
                        BigDecimal giaVe = phimEntity.getGiaVe().multiply(BigDecimal.valueOf(heSoGia));
                        
                        totalVe = totalVe.add(giaVe);
                        vePrices.put(seatId.trim(), giaVe);
                        break;
                    }
                }
            }

            // Lưu vào session
            session.setAttribute("vePrices", vePrices);
            session.setAttribute("totalVe", totalVe);
            session.setAttribute("selectedSeats", selectedSeats);

            // Kiểm tra và lưu vé
            for (String seatId : selectedSeatList) {
                GheEntity selectedGhe = null;
                for (GheEntity ghe : gheList) {
                    if ((ghe.getTenHangAdmin() + ghe.getSoGheAdmin()).equals(seatId.trim())) {
                        selectedGhe = ghe;
                        break;
                    }
                }
                
                if (selectedGhe != null) {
                    // Kiểm tra vé tồn tại
                    Query veQuery = dbSession.createQuery(
                            "FROM VeEntity v WHERE v.maSuatChieu = :maSuatChieu AND v.maGhe = :maGhe");
                    veQuery.setParameter("maSuatChieu", maSuatChieu);
                    veQuery.setParameter("maGhe", selectedGhe.getMaGhe());
                    VeEntity existingVe = (VeEntity) veQuery.uniqueResult();
                    
                    if (existingVe != null) {
                        if (existingVe.getDonHang() != null) {
                            model.addAttribute("error", "Ghế " + seatId + " đã được thanh toán");
                            return showSeatSelection(maPhim, maSuatChieu, session, model);
                        } else if (existingVe.getNgayMua().after(fiveMinutesAgo)) {
                            model.addAttribute("error", "Ghế " + seatId + " đang được giữ. Vui lòng chọn ghế khác.");
                            return showSeatSelection(maPhim, maSuatChieu, session, model);
                        } else {
                            dbSession.delete(existingVe);
                        }
                    }
                    
                    // Tạo vé mới
                    VeEntity ve = new VeEntity();
                    ve.setMaVe("VE" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8));
                    ve.setMaSuatChieu(maSuatChieu);
                    ve.setMaGhe(selectedGhe.getMaGhe());
                    ve.setGiaVe(phimEntity.getGiaVe().multiply(
                        BigDecimal.valueOf(selectedGhe.getLoaiGhe().getHeSoGia())));
                    ve.setNgayMua(new Date());
                    ve.setDonHang(null);
                    dbSession.save(ve);
                }
            }

            session.setAttribute("selectedSeats", selectedSeats);
            session.setAttribute("maPhim", maPhim);
            session.setAttribute("maSuatChieu", maSuatChieu);
            session.setAttribute("seatCountByType", seatCountByType);
            session.setAttribute("reservationStartTime", System.currentTimeMillis());

            messagingTemplate.convertAndSend("/topic/seats/" + maSuatChieu, selectedSeatList);
            return "redirect:/booking/select-food";
            
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Lỗi khi giữ ghế: " + e.getMessage());
            return showSeatSelection(maPhim, maSuatChieu, session, model);
        }
    }


    @Transactional
    @RequestMapping(value = "/update-seats", method = RequestMethod.POST)
    public String updateSeats(@RequestParam("maPhim") String maPhim,
            @RequestParam("maSuatChieu") String maSuatChieu,
            @RequestParam(value = "selectedSeats", required = false) String selectedSeats,
            HttpSession session,
            Model model,
            @RequestParam(value = "fromSelectFood", required = false) String fromSelectFood) {
        try {
            KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
            if (loggedInUser == null) {
                session.setAttribute("redirectMaPhim", maPhim);
                session.setAttribute("redirectMaSuatChieu", maSuatChieu);
                model.addAttribute("error", "Vui lòng đăng nhập để đặt vé");
                return "redirect:/auth/login";
            }

            if ("true".equals(fromSelectFood)) {
                String sessionSeats = (String) session.getAttribute("selectedSeats");
                if (sessionSeats != null && !sessionSeats.isEmpty()) {
                    model.addAttribute("selectedSeats", Arrays.asList(sessionSeats.split(",")));
                }
                Map<String, Integer> seatCountByType = (Map<String, Integer>) session.getAttribute("seatCountByType");
                model.addAttribute("seatCountByType", seatCountByType != null ? seatCountByType : new HashMap<String, Integer>());
                return showSeatSelection(maPhim, maSuatChieu, session, model);
            }

            if (selectedSeats == null || selectedSeats.isEmpty()) {
                model.addAttribute("error", "Vui lòng chọn ít nhất một ghế");
                return showSeatSelection(maPhim, maSuatChieu, session, model);
            }

            return reserveSeats(maPhim, maSuatChieu, selectedSeats, session, model);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Lỗi khi cập nhật ghế: " + e.getMessage());
            return showSeatSelection(maPhim, maSuatChieu, session, model);
        }
    }

    @Transactional
    @RequestMapping(value = "/select-food", method = { RequestMethod.GET, RequestMethod.POST })
    public String showFoodSelection(@RequestParam(value = "maPhim", required = false) String maPhim,
            @RequestParam(value = "maSuatChieu", required = false) String maSuatChieu,
            @RequestParam(value = "selectedSeats", required = false) String selectedSeats,
            HttpSession session,
            Model model) {
        if (maPhim == null || maSuatChieu == null || selectedSeats == null) {
            maPhim = (String) session.getAttribute("maPhim");
            maSuatChieu = (String) session.getAttribute("maSuatChieu");
            selectedSeats = (String) session.getAttribute("selectedSeats");
        }

        if (maPhim == null || maSuatChieu == null || selectedSeats == null || selectedSeats.isEmpty()) {
            model.addAttribute("error", "Thông tin đặt vé không đầy đủ. Vui lòng chọn lại ghế.");
            return "redirect:/home/";
        }

        try {
            KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
            if (loggedInUser == null) {
                session.setAttribute("redirectMaPhim", maPhim);
                session.setAttribute("redirectMaSuatChieu", maSuatChieu);
                model.addAttribute("error", "Vui lòng đăng nhập để đặt vé");
                return "redirect:/auth/login";
            }

            Session dbSession = sessionFactory.getCurrentSession();

            Query phimQuery = dbSession.createQuery(
                    "FROM PhimEntity p LEFT JOIN FETCH p.theLoais LEFT JOIN FETCH p.dienViens WHERE p.maPhim = :maPhim");
            phimQuery.setParameter("maPhim", maPhim);
            PhimEntity phimEntity = (PhimEntity) phimQuery.uniqueResult();
            if (phimEntity == null) {
                model.addAttribute("error", "Phim không tồn tại");
                return "user/book-ticket";
            }

            Query suatChieuQuery = dbSession.createQuery(
                    "SELECT sc, sc.phongChieu, sc.phongChieu.rapChieu FROM SuatChieuEntity sc WHERE sc.maSuatChieu = :maSuatChieu");
            suatChieuQuery.setParameter("maSuatChieu", maSuatChieu);
            Object[] suatChieuResult = (Object[]) suatChieuQuery.uniqueResult();
            if (suatChieuResult == null) {
                model.addAttribute("error", "Suất chiếu không tồn tại");
                return "user/book-ticket";
            }

            SuatChieuEntity suatChieuEntity = (SuatChieuEntity) suatChieuResult[0];
            RapChieuEntity rapChieu = (RapChieuEntity) suatChieuResult[2];

            List<String> selectedSeatList = Arrays.asList(selectedSeats.split(","));
         // *** SỬA: Query từ VIEW ***
            String sql = "SELECT " +
                         "g.MaGhe, g.SoGhe, g.TenHang, g.MaLoaiGhe, g.MaPhongChieu, " +
                         "v.SoGheDisplay, v.TenHangDisplay, " +
                         "lg.MaLoaiGhe AS LgMaLoaiGhe, CAST(lg.TenLoaiGhe AS VARCHAR(50)) AS TenLoaiGhe, lg.HeSoGia, lg.MauGhe, lg.SoCho " +
                         "FROM Ghe g " +
                         "LEFT JOIN V_GheUserView v ON g.MaGhe = v.MaGhe " +
                         "LEFT JOIN LoaiGhe lg ON g.MaLoaiGhe = lg.MaLoaiGhe " +
                         "WHERE g.MaPhongChieu = :maPhongChieu " +
                         "ORDER BY v.ThuTuHang, v.SoGheDisplay";

            Query gheQuery = dbSession.createSQLQuery(sql);
            gheQuery.setParameter("maPhongChieu", suatChieuEntity.getPhongChieu().getMaPhongChieu());
            List<Object[]> results = gheQuery.list();

            List<GheEntity> gheList = new ArrayList<>();
            for (Object[] row : results) {
                GheEntity ghe = new GheEntity();
                ghe.setMaGhe((String) row[0]);
                ghe.setSoGhe((String) row[1]);
                ghe.setTenHang((String) row[2]);
                ghe.setSoGheAdmin((String) row[5]);
                ghe.setTenHangAdmin((String) row[6]);
                
                if (row[7] != null) {
                    LoaiGheEntity loaiGhe = new LoaiGheEntity();
                    loaiGhe.setMaLoaiGhe((String) row[7]);
                    loaiGhe.setTenLoaiGhe((String) row[8]);
                    loaiGhe.setHeSoGia(((Number) row[9]).doubleValue());
                    loaiGhe.setMauGhe((String) row[10]);
                    loaiGhe.setSoCho(((Number) row[11]).intValue());
                    ghe.setLoaiGhe(loaiGhe);
                }
                
                gheList.add(ghe);
            }

            Map<String, BigDecimal> vePrices = new HashMap<>();
            BigDecimal totalVe = BigDecimal.ZERO;
            for (String seatId : selectedSeatList) {
                for (GheEntity ghe : gheList) {
                    String fullSeatId = ghe.getTenHangAdmin() + ghe.getSoGheAdmin();
                    if (fullSeatId.equals(seatId.trim())) {
                        BigDecimal giaVe = phimEntity.getGiaVe().multiply(BigDecimal.valueOf(ghe.getLoaiGhe().getHeSoGia()));
                        vePrices.put(seatId, giaVe);
                        totalVe = totalVe.add(giaVe);
                        break;
                    }
                }
            }

            Set<PhuThuEntity> phuThus = suatChieuEntity.getPhuThus();
            BigDecimal tongPhuThu = BigDecimal.ZERO;
            for (PhuThuEntity phuThu : phuThus) {
                tongPhuThu = tongPhuThu.add(phuThu.getGiaPhuThu());
            }
            BigDecimal totalPhuThu = tongPhuThu.multiply(new BigDecimal(selectedSeatList.size()));
            BigDecimal tongTien = totalVe.add(totalPhuThu);

            Query comboQuery = dbSession.createQuery("FROM ComboEntity c");
            List<ComboEntity> comboEntities = comboQuery.list();
            List<ComboModel> combos = new ArrayList<>();
            for (ComboEntity entity : comboEntities) {
                combos.add(new ComboModel(entity));
            }

            Query bapNuocQuery = dbSession.createQuery("FROM BapNuocEntity b");
            List<BapNuocEntity> bapNuocEntities = bapNuocQuery.list();
            List<BapNuocModel> bapNuocs = new ArrayList<>();
            for (BapNuocEntity entity : bapNuocEntities) {
                bapNuocs.add(new BapNuocModel(entity));
            }

            model.addAttribute("phim", new PhimModel(phimEntity));
            model.addAttribute("rapChieu", new RapChieuModel(rapChieu));
            model.addAttribute("suatChieu", new SuatChieuModel(suatChieuEntity));
            model.addAttribute("vePrices", vePrices);
            model.addAttribute("tongTien", tongTien);
            model.addAttribute("selectedSeats", selectedSeats);
            model.addAttribute("combos", combos);
            model.addAttribute("bapNuocs", bapNuocs);
            model.addAttribute("maPhim", maPhim);
            model.addAttribute("maSuatChieu", maSuatChieu);

            session.setAttribute("selectedSeats", selectedSeats);
            session.setAttribute("maPhim", maPhim);
            session.setAttribute("maSuatChieu", maSuatChieu);
            session.setAttribute("vePrices", vePrices);
            session.setAttribute("tongTien", tongTien);

            return "user/select-food";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Lỗi khi tải trang chọn combo/bắp nước: " + e.getMessage());
            return "user/book-ticket";
        }
    }

    @Transactional
    @RequestMapping(value = "/select-payment", method = RequestMethod.POST)
    public String showPayment(@RequestParam("maPhim") String maPhim,
            @RequestParam("maSuatChieu") String maSuatChieu,
            @RequestParam("selectedSeats") String selectedSeats,
            @RequestParam Map<String, String> allParams,
            HttpSession session,
            Model model) {
        try {
            KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
            if (loggedInUser == null) {
                session.setAttribute("redirectMaPhim", maPhim);
                session.setAttribute("redirectMaSuatChieu", maSuatChieu);
                model.addAttribute("error", "Vui lòng đăng nhập để đặt vé");
                return "redirect:/auth/login";
            }

            Session dbSession = sessionFactory.getCurrentSession();

            Query phimQuery = dbSession.createQuery(
                    "FROM PhimEntity p LEFT JOIN FETCH p.theLoais LEFT JOIN FETCH p.dienViens WHERE p.maPhim = :maPhim");
            phimQuery.setParameter("maPhim", maPhim);
            PhimEntity phimEntity = (PhimEntity) phimQuery.uniqueResult();
            if (phimEntity == null) {
                model.addAttribute("error", "Phim không tồn tại");
                return "user/select-food";
            }

            Query suatChieuQuery = dbSession.createQuery(
                    "SELECT sc, sc.phongChieu, sc.phongChieu.rapChieu FROM SuatChieuEntity sc WHERE sc.maSuatChieu = :maSuatChieu");
            suatChieuQuery.setParameter("maSuatChieu", maSuatChieu);
            Object[] suatChieuResult = (Object[]) suatChieuQuery.uniqueResult();
            if (suatChieuResult == null) {
                model.addAttribute("error", "Suất chiếu không tồn tại");
                return "user/select-food";
            }

            SuatChieuEntity suatChieuEntity = (SuatChieuEntity) suatChieuResult[0];
            RapChieuEntity rapChieu = (RapChieuEntity) suatChieuResult[2];

            List<String> selectedSeatList = Arrays.asList(selectedSeats.split(","));
         // *** SỬA: Query từ VIEW ***
            String sql = "SELECT " +
                         "g.MaGhe, g.SoGhe, g.TenHang, g.MaLoaiGhe, g.MaPhongChieu, " +
                         "v.SoGheDisplay, v.TenHangDisplay, " +
                         "lg.MaLoaiGhe AS LgMaLoaiGhe, CAST(lg.TenLoaiGhe AS VARCHAR(50)) AS TenLoaiGhe, lg.HeSoGia, lg.MauGhe, lg.SoCho " +
                         "FROM Ghe g " +
                         "LEFT JOIN V_GheUserView v ON g.MaGhe = v.MaGhe " +
                         "LEFT JOIN LoaiGhe lg ON g.MaLoaiGhe = lg.MaLoaiGhe " +
                         "WHERE g.MaPhongChieu = :maPhongChieu " +
                         "ORDER BY v.ThuTuHang, v.SoGheDisplay";

            Query gheQuery = dbSession.createSQLQuery(sql);
            gheQuery.setParameter("maPhongChieu", suatChieuEntity.getPhongChieu().getMaPhongChieu());
            List<Object[]> results = gheQuery.list();

            List<GheEntity> gheList = new ArrayList<>();
            for (Object[] row : results) {
                GheEntity ghe = new GheEntity();
                ghe.setMaGhe((String) row[0]);
                ghe.setSoGhe((String) row[1]);
                ghe.setTenHang((String) row[2]);
                ghe.setSoGheAdmin((String) row[5]);
                ghe.setTenHangAdmin((String) row[6]);
                
                if (row[7] != null) {
                    LoaiGheEntity loaiGhe = new LoaiGheEntity();
                    loaiGhe.setMaLoaiGhe((String) row[7]);
                    loaiGhe.setTenLoaiGhe((String) row[8]);  // Đã CAST
                    loaiGhe.setHeSoGia(((Number) row[9]).doubleValue());
                    loaiGhe.setMauGhe((String) row[10]);
                    loaiGhe.setSoCho(((Number) row[11]).intValue());
                    ghe.setLoaiGhe(loaiGhe);
                }
                
                gheList.add(ghe);
            }


            Map<String, BigDecimal> vePrices = new HashMap<>();
            BigDecimal totalVe = BigDecimal.ZERO;
            for (String seatId : selectedSeatList) {
                for (GheEntity ghe : gheList) {
                    String fullSeatId = ghe.getTenHangAdmin() + ghe.getSoGheAdmin();
                    if (fullSeatId.equals(seatId.trim())) {
                        double heSoGia = ghe.getLoaiGhe().getHeSoGia();
                        BigDecimal giaVe = phimEntity.getGiaVe().multiply(BigDecimal.valueOf(heSoGia));
                        vePrices.put(seatId.trim(), giaVe);
                        totalVe = totalVe.add(giaVe);
                        break;
                    }
                }
            }

            Set<PhuThuEntity> phuThus = suatChieuEntity.getPhuThus();
            BigDecimal tongPhuThu = BigDecimal.ZERO;
            List<PhuThuModel> phuThuList = new ArrayList<>();
            for (PhuThuEntity phuThu : phuThus) {
                tongPhuThu = tongPhuThu.add(phuThu.getGiaPhuThu());
                phuThuList.add(new PhuThuModel(phuThu));
            }

            Map<String, Integer> selectedCombos = new HashMap<>();
            Map<String, BigDecimal> comboPrices = new HashMap<>();
            BigDecimal totalCombo = BigDecimal.ZERO;
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("combo_")) {
                    String maCombo = entry.getKey().substring(6);
                    int quantity;
                    try {
                        quantity = Integer.parseInt(entry.getValue());
                    } catch (NumberFormatException e) {
                        quantity = 0;
                    }
                    if (quantity > 0) {
                        selectedCombos.put(maCombo, quantity);
                        Query comboQuery = dbSession.createQuery("FROM ComboEntity c WHERE c.maCombo = :maCombo");
                        comboQuery.setParameter("maCombo", maCombo);
                        ComboEntity combo = (ComboEntity) comboQuery.uniqueResult();
                        if (combo != null) {
                            BigDecimal giaCombo = combo.getGiaCombo().multiply(BigDecimal.valueOf(quantity));
                            comboPrices.put(maCombo, giaCombo);
                            totalCombo = totalCombo.add(giaCombo);
                        }
                    }
                }
            }

            Map<String, Integer> selectedBapNuocs = new HashMap<>();
            Map<String, BigDecimal> bapNuocPrices = new HashMap<>();
            BigDecimal totalBapNuoc = BigDecimal.ZERO;
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                if (entry.getKey().startsWith("bapNuoc_")) {
                    String maBapNuoc = entry.getKey().substring(8);
                    int quantity;
                    try {
                        quantity = Integer.parseInt(entry.getValue());
                    } catch (NumberFormatException e) {
                        quantity = 0;
                    }
                    if (quantity > 0) {
                        selectedBapNuocs.put(maBapNuoc, quantity);
                        Query bapNuocQuery = dbSession.createQuery("FROM BapNuocEntity b WHERE b.maBapNuoc = :maBapNuoc");
                        bapNuocQuery.setParameter("maBapNuoc", maBapNuoc);
                        BapNuocEntity bapNuoc = (BapNuocEntity) bapNuocQuery.uniqueResult();
                        if (bapNuoc != null) {
                            BigDecimal giaBapNuoc = bapNuoc.getGiaBapNuoc().multiply(BigDecimal.valueOf(quantity));
                            bapNuocPrices.put(maBapNuoc, giaBapNuoc);
                            totalBapNuoc = totalBapNuoc.add(giaBapNuoc);
                        }
                    }
                }
            }

            BigDecimal tongTien = totalVe.add(totalCombo).add(totalBapNuoc).add(tongPhuThu.multiply(new BigDecimal(selectedSeatList.size())));

            session.setAttribute("selectedSeats", selectedSeats);
            session.setAttribute("maPhim", maPhim);
            session.setAttribute("maSuatChieu", maSuatChieu);
            session.setAttribute("selectedCombos", selectedCombos);
            session.setAttribute("selectedBapNuocs", selectedBapNuocs);
            session.setAttribute("vePrices", vePrices);
            session.setAttribute("comboPrices", comboPrices);
            session.setAttribute("bapNuocPrices", bapNuocPrices);
            session.setAttribute("tongTien", tongTien);
            session.setAttribute("originTongTien", tongTien);
            session.setAttribute("phuThuList", phuThuList);
            session.setAttribute("tongPhuThu", tongPhuThu);

            model.addAttribute("phim", new PhimModel(phimEntity));
            model.addAttribute("rapChieu", new RapChieuModel(rapChieu));
            model.addAttribute("suatChieu", new SuatChieuModel(suatChieuEntity));
            model.addAttribute("tongTien", tongTien);
            model.addAttribute("selectedSeats", selectedSeats);
            model.addAttribute("selectedCombos", selectedCombos);
            model.addAttribute("selectedBapNuocs", selectedBapNuocs);
            model.addAttribute("vePrices", vePrices);
            model.addAttribute("comboPrices", comboPrices);
            model.addAttribute("bapNuocPrices", bapNuocPrices);
            model.addAttribute("phuThuList", phuThuList);
            model.addAttribute("tongPhuThu", tongPhuThu);

            return "user/payment";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Lỗi khi tải trang thanh toán: " + e.getMessage());
            return "user/select-food";
        }
    }

    @Transactional
    @RequestMapping(value = "/apply-promo-code", method = RequestMethod.POST)
    public String applyPromoCode(@RequestParam("promoCode") String promoCode,
            HttpSession session,
            Model model) {
        try {
            // Kiểm tra promoCode
            if (promoCode == null || promoCode.trim().isEmpty()) {
                model.addAttribute("error", "Vui lòng nhập mã khuyến mãi!");
                return "user/payment";
            }
            promoCode = promoCode.trim();
            System.out.println("Applying promoCode: " + promoCode);

            // Kiểm tra session attributes
            String selectedSeats = (String) session.getAttribute("selectedSeats");
            if (selectedSeats == null || selectedSeats.isEmpty()) {
                model.addAttribute("error", "Thông tin đặt vé không đầy đủ. Vui lòng chọn lại ghế.");
                return "redirect:/home/";
            }

            // Lấy tổng tiền
            BigDecimal tongTien = (BigDecimal) session.getAttribute("originTongTien");
            if (tongTien == null) {
                tongTien = (BigDecimal) session.getAttribute("tongTien");
                if (tongTien == null) {
                    model.addAttribute("error", "Không tìm thấy thông tin đơn hàng!");
                    return "redirect:/home/";
                }
                session.setAttribute("originTongTien", tongTien);
            }
            System.out.println("tongTien: " + tongTien);

            // Truy vấn khuyến mãi
            Session dbSession = sessionFactory.getCurrentSession();
            Query khuyenMaiQuery = dbSession.createQuery(
                    "FROM KhuyenMaiEntity k WHERE k.maCode = :maCode AND :currentDate BETWEEN k.ngayBatDau AND k.ngayKetThuc");
            khuyenMaiQuery.setParameter("maCode", promoCode);
            khuyenMaiQuery.setParameter("currentDate", new Date());
            KhuyenMaiEntity khuyenMai = (KhuyenMaiEntity) khuyenMaiQuery.uniqueResult();

            if (khuyenMai == null) {
                model.addAttribute("error", "Mã khuyến mãi không hợp lệ hoặc đã hết hạn!");
                return "user/payment";
            }
            System.out.println("khuyenMai: " + khuyenMai.getMaCode());

            // Kiểm tra loại giảm giá
            String loaiGiamGia = khuyenMai.getLoaiGiamGia();
            if (!"Phần trăm".equals(loaiGiamGia) && !"Cố định".equals(loaiGiamGia)) {
                model.addAttribute("error", "Loại giảm giá không hợp lệ!");
                return "user/payment";
            }

            // Tính toán giảm giá
            BigDecimal discountAmount;
            BigDecimal newTotal;
            if ("Phần trăm".equals(loaiGiamGia)) {
                BigDecimal discountPercentage = khuyenMai.getGiaTriGiam().divide(new BigDecimal("100"));
                discountAmount = tongTien.multiply(discountPercentage);
                newTotal = tongTien.subtract(discountAmount);
            } else {
                discountAmount = khuyenMai.getGiaTriGiam();
                newTotal = tongTien.subtract(discountAmount);
                if (newTotal.compareTo(BigDecimal.ZERO) < 0) {
                    newTotal = BigDecimal.ZERO;
                }
            }

            // Lưu thông tin vào session
            session.setAttribute("appliedPromoCode", promoCode);
            session.setAttribute("discountAmount", discountAmount);
            session.setAttribute("maKhuyenMai", khuyenMai.getMaKhuyenMai());
            session.setAttribute("tongTien", newTotal);

            // Cập nhật model
            model.addAttribute("promoCode", promoCode);
            model.addAttribute("khuyenMai", khuyenMai);
            model.addAttribute("discountAmount", discountAmount);
            model.addAttribute("tongTien", newTotal);
            model.addAttribute("success", "Mã khuyến mãi đã được áp dụng thành công!");
            model.addAttribute("selectedSeats", selectedSeats);
            model.addAttribute("selectedCombos", session.getAttribute("selectedCombos"));
            model.addAttribute("selectedBapNuocs", session.getAttribute("selectedBapNuocs"));
            model.addAttribute("vePrices", session.getAttribute("vePrices"));
            model.addAttribute("comboPrices", session.getAttribute("comboPrices"));
            model.addAttribute("bapNuocPrices", session.getAttribute("bapNuocPrices"));
            model.addAttribute("phuThuList", session.getAttribute("phuThuList"));
            model.addAttribute("tongPhuThu", session.getAttribute("tongPhuThu"));

            return "user/payment";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Lỗi khi áp dụng mã giảm giá: " + e.getMessage());
            return "user/payment";
        }
    }

    @Transactional
    @RequestMapping(value = "/apply-points", method = RequestMethod.POST)
    public String applyPoints(@RequestParam("points") String pointsStr,
            @RequestParam("maPhim") String maPhim,
            @RequestParam("maSuatChieu") String maSuatChieu,
            @RequestParam("selectedSeats") String selectedSeats,
            @RequestParam(value = "promoCode", required = false) String promoCode,
            HttpSession session,
            Model model) {
        try {
            KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
            if (loggedInUser == null) {
                model.addAttribute("error", "Vui lòng đăng nhập để sử dụng điểm");
                return "user/payment";
            }

            // Kiểm tra số điểm
            int tongDiem = loggedInUser.getTongDiem();
            int points;
            try {
                points = Integer.parseInt(pointsStr);
            } catch (NumberFormatException e) {
                model.addAttribute("error", "Số điểm không hợp lệ!");
                return "user/payment";
            }

            if (points < 0) {
                model.addAttribute("error", "Số điểm không được âm!");
                return "user/payment";
            }

            if (points > tongDiem) {
                model.addAttribute("error", "Số điểm nhập vào vượt quá số điểm bạn có!");
                return "user/payment";
            }

            // Lấy tổng tiền
            BigDecimal tongTien = (BigDecimal) session.getAttribute("originTongTien");
            if (tongTien == null) {
                tongTien = (BigDecimal) session.getAttribute("tongTien");
                if (tongTien == null) {
                    model.addAttribute("error", "Không tìm thấy thông tin đơn hàng!");
                    return "user/payment";
                }
                session.setAttribute("originTongTien", tongTien);
            }

            // Kiểm tra mã khuyến mãi (nếu có)
            Session dbSession = sessionFactory.getCurrentSession();
            BigDecimal discountAmount = BigDecimal.ZERO;
            KhuyenMaiEntity khuyenMai = null;
            if (promoCode != null && !promoCode.isEmpty()) {
                Query khuyenMaiQuery = dbSession.createQuery(
                        "FROM KhuyenMaiEntity k WHERE k.maCode = :maCode AND :currentDate BETWEEN k.ngayBatDau AND k.ngayKetThuc");
                khuyenMaiQuery.setParameter("maCode", promoCode);
                khuyenMaiQuery.setParameter("currentDate", new Date());
                khuyenMai = (KhuyenMaiEntity) khuyenMaiQuery.uniqueResult();
                if (khuyenMai != null) {
                    if ("Phần trăm".equals(khuyenMai.getLoaiGiamGia())) {
                        BigDecimal discountPercentage = khuyenMai.getGiaTriGiam().divide(new BigDecimal("100"));
                        discountAmount = tongTien.multiply(discountPercentage);
                    } else {
                        discountAmount = khuyenMai.getGiaTriGiam();
                    }
                    session.setAttribute("discountAmount", discountAmount);
                    session.setAttribute("maKhuyenMai", khuyenMai.getMaKhuyenMai());
                    session.setAttribute("appliedPromoCode", promoCode);
                }
            }
            tongTien = tongTien.subtract(discountAmount);

            // Tính toán giảm giá từ điểm
            BigDecimal pointsDiscount = new BigDecimal(points).multiply(new BigDecimal("10"));
            BigDecimal newTotal = tongTien.subtract(pointsDiscount);

            if (newTotal.compareTo(BigDecimal.ZERO) < 0) {
                BigDecimal maxPoints = tongTien.divide(new BigDecimal("10"), 0, BigDecimal.ROUND_DOWN);
                points = maxPoints.intValue();
                pointsDiscount = new BigDecimal(points).multiply(new BigDecimal("10"));
                newTotal = tongTien.subtract(pointsDiscount);
            }

            // Lưu thông tin vào session
            session.setAttribute("appliedPoints", points);
            session.setAttribute("pointsDiscount", pointsDiscount);
            session.setAttribute("tongTien", newTotal);

            // Cập nhật model
            model.addAttribute("appliedPoints", points);
            model.addAttribute("pointsDiscount", pointsDiscount);
            model.addAttribute("tongTien", newTotal);
            model.addAttribute("promoCode", promoCode);
            model.addAttribute("success", "Điểm đã được áp dụng thành công!");
            model.addAttribute("selectedSeats", selectedSeats);
            model.addAttribute("maPhim", maPhim);
            model.addAttribute("maSuatChieu", maSuatChieu);
            model.addAttribute("selectedCombos", session.getAttribute("selectedCombos"));
            model.addAttribute("selectedBapNuocs", session.getAttribute("selectedBapNuocs"));
            model.addAttribute("vePrices", session.getAttribute("vePrices"));
            model.addAttribute("comboPrices", session.getAttribute("comboPrices"));
            model.addAttribute("bapNuocPrices", session.getAttribute("bapNuocPrices"));
            model.addAttribute("phuThuList", session.getAttribute("phuThuList"));
            model.addAttribute("tongPhuThu", session.getAttribute("tongPhuThu"));
            model.addAttribute("discountAmount", discountAmount);
            model.addAttribute("khuyenMai", khuyenMai);

            return "user/payment";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Lỗi khi áp dụng điểm: " + e.getMessage());
            return "user/payment";
        }
    }

    @Transactional
    @RequestMapping(value = "/confirm-payment", method = RequestMethod.POST)
    public String confirmPayment(
            @RequestParam("paymentMethod") String paymentMethod,
            @RequestParam(value = "promoCode", required = false) String promoCode,
            HttpSession session,
            HttpServletRequest request,
            Model model) {
        
        // Lấy thông tin từ session
        KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
        String selectedSeats = (String) session.getAttribute("selectedSeats");
        String maPhim = (String) session.getAttribute("maPhim");
        String maSuatChieu = (String) session.getAttribute("maSuatChieu");
        Map<String, Integer> selectedCombos = (Map<String, Integer>) session.getAttribute("selectedCombos");
        Map<String, Integer> selectedBapNuocs = (Map<String, Integer>) session.getAttribute("selectedBapNuocs");
        Map<String, BigDecimal> vePrices = (Map<String, BigDecimal>) session.getAttribute("vePrices");
        Map<String, BigDecimal> comboPrices = (Map<String, BigDecimal>) session.getAttribute("comboPrices");
        Map<String, BigDecimal> bapNuocPrices = (Map<String, BigDecimal>) session.getAttribute("bapNuocPrices");
        BigDecimal tongTien = (BigDecimal) session.getAttribute("tongTien");
        String maKhuyenMai = (String) session.getAttribute("maKhuyenMai");
        BigDecimal discountAmount = (BigDecimal) session.getAttribute("discountAmount");
        List<PhuThuModel> phuThuList = (List<PhuThuModel>) session.getAttribute("phuThuList");
        BigDecimal tongPhuThu = (BigDecimal) session.getAttribute("tongPhuThu");
        Integer appliedPoints = (Integer) session.getAttribute("appliedPoints");
        BigDecimal pointsDiscount = (BigDecimal) session.getAttribute("pointsDiscount");

        // Kiểm tra hợp lệ
        if (loggedInUser == null) {
            model.addAttribute("error", "Vui lòng đăng nhập để thanh toán");
            return "user/payment";
        }
        if (tongTien == null || tongTien.compareTo(BigDecimal.ZERO) <= 0) {
            model.addAttribute("error", "Tổng tiền không hợp lệ");
            return "user/payment";
        }
        if (selectedSeats == null || selectedSeats.isEmpty() || maPhim == null || maSuatChieu == null) {
            model.addAttribute("error", "Thông tin đặt vé không đầy đủ");
            return "user/payment";
        }

        try {
            Session dbSession = sessionFactory.getCurrentSession();

            // Tạo đơn hàng tạm thời
            DonHangEntity donHang = new DonHangEntity();
            String maDonHang = "DH" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8);
            donHang.setMaDonHang(maDonHang);
            donHang.setMaKhachHang(loggedInUser.getMaKhachHang());
            if (maKhuyenMai != null && !maKhuyenMai.isEmpty()) {
                donHang.setMaKhuyenMai(maKhuyenMai);
            }
            donHang.setTongTien(tongTien);
            donHang.setTrangThaiDonHang("Chờ thanh toán"); // Chưa thanh toán
            donHang.setNgayDat(new Date());
            donHang.setDiemSuDung(appliedPoints != null ? appliedPoints : 0);

            // Xử lý thanh toán MoMo
            if ("Bypass".equalsIgnoreCase(paymentMethod)) {
                // BYPASS PAYMENT (TEST MODE)
                return processBypassPayment(session, model, donHang, dbSession);
            } else if ("MoMo".equalsIgnoreCase(paymentMethod)) {
                try {
                    // Lưu đơn hàng tạm vào session (chưa save DB)
                    session.setAttribute("pendingOrder", donHang);
                    session.setAttribute("pendingMaDonHang", maDonHang);
                    
                    // Chuẩn bị request MoMo
                    String orderId = maDonHang;
                    String requestId = "REQ" + System.currentTimeMillis();
                    String orderInfo = "Thanh toan ve xem phim " + maPhim;
                    long amount = tongTien.longValue();
                    
                    String returnUrl = MomoConfig.getReturnUrl(request);
                    String notifyUrl = MomoConfig.getNotifyUrl(request);
                    
                    // Tạo chuỗi rawData để ký (theo đúng thứ tự alphabet của MoMo)
                    String rawData = "accessKey=" + MomoConfig.momo_AccessKey +
                                    "&amount=" + amount +
                                    "&extraData=" + "" +
                                    "&ipnUrl=" + notifyUrl +
                                    "&orderId=" + orderId +
                                    "&orderInfo=" + orderInfo +
                                    "&partnerCode=" + MomoConfig.momo_PartnerCode +
                                    "&redirectUrl=" + returnUrl +
                                    "&requestId=" + requestId +
                                    "&requestType=" + MomoConfig.momo_RequestType;
                    
                    // Tạo signature HMAC-SHA256
                    String signature = computeHmacSha256(rawData, MomoConfig.momo_SecretKey);
                    
                    // Tạo JSON request payload
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, Object> requestData = new HashMap<>();
                    requestData.put("partnerCode", MomoConfig.momo_PartnerCode);
                    requestData.put("accessKey", MomoConfig.momo_AccessKey);
                    requestData.put("requestId", requestId);
                    requestData.put("amount", amount);
                    requestData.put("orderId", orderId);
                    requestData.put("orderInfo", orderInfo);
                    requestData.put("redirectUrl", returnUrl);
                    requestData.put("ipnUrl", notifyUrl);
                    requestData.put("requestType", MomoConfig.momo_RequestType);
                    requestData.put("extraData", "");
                    requestData.put("lang", "vi");
                    requestData.put("signature", signature);
                    
                    // Gửi HTTP POST request đến MoMo API
                    HttpClient client = HttpClient.newHttpClient();
                    HttpRequest httpRequest = HttpRequest.newBuilder()
                            .uri(URI.create(MomoConfig.momo_Url))
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(requestData)))
                            .build();
                    
                    HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                    
                    // Log để debug
                    System.out.println("=== MoMo Payment Request ===");
                    System.out.println("Request Data: " + mapper.writeValueAsString(requestData));
                    System.out.println("=== MoMo Payment Response ===");
                    System.out.println(response.body());
                    
                    // Parse response từ MoMo
                    MomoCreatePaymentResponseModel responseModel = mapper.readValue(
                        response.body(), 
                        MomoCreatePaymentResponseModel.class
                    );
                    
                    // Kiểm tra response
                    if (responseModel.getResultCode() != null && responseModel.getResultCode() == 0) {
                        // Thành công - Lưu thông tin vào session
                        session.setAttribute("momoPayUrl", responseModel.getPayUrl());
                        session.setAttribute("momoOrderId", orderId);
                        
                        System.out.println("✓ MoMo Pay URL: " + responseModel.getPayUrl());
                        
                        // REDIRECT TRỰC TIẾP SANG TRANG THANH TOÁN MOMO
                        return "redirect:" + responseModel.getPayUrl();
                        
                    } else {
                        // Lỗi từ MoMo
                        String errorMsg = responseModel.getMessage() != null ? 
                                         responseModel.getMessage() : "Không thể tạo thanh toán MoMo";
                        
                        System.err.println("✗ MoMo Error: " + errorMsg);
                        System.err.println("✗ Result Code: " + responseModel.getResultCode());
                        System.err.println("✗ Full Response: " + response.body());
                        
                        model.addAttribute("error", errorMsg + " (Mã lỗi: " + responseModel.getResultCode() + ")");
                        return "user/payment";
                    }
                    
                } catch (Exception momoEx) {
                    momoEx.printStackTrace();
                    System.err.println("✗ Exception khi gọi MoMo API: " + momoEx.getMessage());
                    model.addAttribute("error", "Lỗi kết nối với MoMo: " + momoEx.getMessage());
                    return "user/payment";
                }
            }
            
            // Nếu không phải MoMo (các phương thức khác)
            model.addAttribute("error", "Phương thức thanh toán chưa được hỗ trợ: " + paymentMethod);
            return "user/payment";
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("✗ Exception trong confirmPayment: " + e.getMessage());
            model.addAttribute("error", "Lỗi khi xác nhận thanh toán: " + e.getMessage());
            return "user/payment";
        }
    }

    @Transactional
    private String processBypassPayment(HttpSession session, Model model, DonHangEntity donHang, Session dbSession) {
        try {
            System.out.println("=== BYPASS PAYMENT (TEST MODE) ===");
            
            // Lấy thông tin từ session
            KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
            String selectedSeats = (String) session.getAttribute("selectedSeats");
            String maSuatChieu = (String) session.getAttribute("maSuatChieu");
            String maPhim = (String) session.getAttribute("maPhim");
            Map<String, Integer> selectedCombos = (Map<String, Integer>) session.getAttribute("selectedCombos");
            Map<String, Integer> selectedBapNuocs = (Map<String, Integer>) session.getAttribute("selectedBapNuocs");
            Integer appliedPoints = (Integer) session.getAttribute("appliedPoints");
            
            // 1. Cập nhật trạng thái đơn hàng
            donHang.setTrangThaiDonHang("Đã thanh toán");
            dbSession.save(donHang);
            System.out.println("✓ Đã tạo đơn hàng: " + donHang.getMaDonHang());
            
            // 2. Gán vé tạm vào đơn hàng
            List<String> selectedSeatList = Arrays.asList(selectedSeats.split(","));
            
            String sql = "SELECT " +
                         "g.MaGhe, g.SoGhe, g.TenHang, g.MaLoaiGhe, g.MaPhongChieu, " +
                         "v.SoGheDisplay, v.TenHangDisplay, " +
                         "lg.MaLoaiGhe AS LgMaLoaiGhe, CAST(lg.TenLoaiGhe AS VARCHAR(50)) AS TenLoaiGhe, " +
                         "lg.HeSoGia, lg.MauGhe, lg.SoCho " +
                         "FROM Ghe g " +
                         "LEFT JOIN V_GheUserView v ON g.MaGhe = v.MaGhe " +
                         "LEFT JOIN LoaiGhe lg ON g.MaLoaiGhe = lg.MaLoaiGhe " +
                         "WHERE g.MaPhongChieu = (SELECT MaPhongChieu FROM SuatChieu WHERE MaSuatChieu = :maSuatChieu)";
            
            Query gheQuery = dbSession.createSQLQuery(sql);
            gheQuery.setParameter("maSuatChieu", maSuatChieu);
            List<Object[]> gheResults = gheQuery.list();
            
            List<GheEntity> gheList = new ArrayList<>();
            for (Object[] row : gheResults) {
                GheEntity ghe = new GheEntity();
                ghe.setMaGhe((String) row[0]);
                ghe.setSoGheAdmin((String) row[5]);
                ghe.setTenHangAdmin((String) row[6]);
                gheList.add(ghe);
            }
            
            Query veQuery = dbSession.createQuery(
                "FROM VeEntity v WHERE v.maSuatChieu = :maSuatChieu AND v.donHang IS NULL");
            veQuery.setParameter("maSuatChieu", maSuatChieu);
            List<VeEntity> pendingVes = veQuery.list();
            
            // *** THÊM: Map giá vé cho từng ghế ***
            Map<String, BigDecimal> vePrices = new HashMap<>();
            
            int updatedCount = 0;
            for (VeEntity ve : pendingVes) {
                for (GheEntity ghe : gheList) {
                    if (ghe.getMaGhe().equals(ve.getMaGhe())) {
                        String seatId = ghe.getTenHangAdmin() + ghe.getSoGheAdmin();
                        if (selectedSeatList.contains(seatId.trim())) {
                            ve.setDonHang(donHang);
                            dbSession.update(ve);
                            vePrices.put(seatId, ve.getGiaVe());  // ← Lưu giá vé
                            updatedCount++;
                        }
                        break;
                    }
                }
            }
            System.out.println("✓ Đã gán " + updatedCount + " vé vào đơn hàng");
            
            // 3. Lưu chi tiết combo
            Map<String, String> comboNames = new HashMap<>();
            Map<String, BigDecimal> comboPrices = new HashMap<>();
            
            if (selectedCombos != null && !selectedCombos.isEmpty()) {
                for (Map.Entry<String, Integer> entry : selectedCombos.entrySet()) {
                    ChiTietDonHangComboEntity chiTiet = new ChiTietDonHangComboEntity();
                    chiTiet.setDonHang(donHang);
                    chiTiet.setMaCombo(entry.getKey());
                    chiTiet.setSoLuong(entry.getValue());
                    dbSession.save(chiTiet);
                    
                    // Lấy tên và giá combo
                    ComboEntity combo = (ComboEntity) dbSession.get(ComboEntity.class, entry.getKey());
                    if (combo != null) {
                        comboNames.put(entry.getKey(), combo.getTenCombo());
                        comboPrices.put(entry.getKey(), combo.getGiaCombo().multiply(new BigDecimal(entry.getValue())));
                    }
                }
            }
            
            // 4. Lưu chi tiết bắp nước
            Map<String, String> bapNuocNames = new HashMap<>();
            Map<String, BigDecimal> bapNuocPrices = new HashMap<>();
            
            if (selectedBapNuocs != null && !selectedBapNuocs.isEmpty()) {
                for (Map.Entry<String, Integer> entry : selectedBapNuocs.entrySet()) {
                    ChiTietDonHangBapNuocEntity chiTiet = new ChiTietDonHangBapNuocEntity();
                    chiTiet.setDonHang(donHang);
                    chiTiet.setMaBapNuoc(entry.getKey());
                    chiTiet.setSoLuong(entry.getValue());
                    dbSession.save(chiTiet);
                    
                    // Lấy tên và giá bắp nước
                    BapNuocEntity bapNuoc = (BapNuocEntity) dbSession.get(BapNuocEntity.class, entry.getKey());
                    if (bapNuoc != null) {
                        bapNuocNames.put(entry.getKey(), bapNuoc.getTenBapNuoc());
                        bapNuocPrices.put(entry.getKey(), bapNuoc.getGiaBapNuoc().multiply(new BigDecimal(entry.getValue())));
                    }
                }
            }
            
            // 5. Lưu thông tin thanh toán
            ThanhToanEntity thanhToan = new ThanhToanEntity();
            thanhToan.setMaThanhToan("TT" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8));
            thanhToan.setDonHang(donHang);
            thanhToan.setPhuongThuc("Bypass (Test)");  // ← Phương thức
            thanhToan.setSoTien(donHang.getTongTien());
            thanhToan.setNgayThanhToan(new Date());
            thanhToan.setTrangThai("Success");
            dbSession.save(thanhToan);
            
            // 6. Cập nhật điểm
            if (loggedInUser != null) {
                KhachHangEntity khachHang = (KhachHangEntity) dbSession.get(
                    KhachHangEntity.class, loggedInUser.getMaKhachHang());
                
                if (khachHang != null) {
                    if (appliedPoints != null && appliedPoints > 0) {
                        khachHang.setTongDiem(khachHang.getTongDiem() - appliedPoints);
                    }
                    
                    int earnedPoints = donHang.tinhDiem();
                    if (earnedPoints > 0) {
                        khachHang.congDiem(earnedPoints);
                    }
                    
                    dbSession.update(khachHang);
                    loggedInUser.setTongDiem(khachHang.getTongDiem());
                    session.setAttribute("loggedInUser", loggedInUser);
                }
            }
            
            // 7. Lấy phụ thu (nếu có)
            String phuThuSql = 
                    "SELECT pt.MaPhuThu, pt.TenPhuThu, pt.Gia " +
                    "FROM PhuThu pt " +
                    "INNER JOIN PhuThuSuatChieu ptsc ON pt.MaPhuThu = ptsc.MaPhuThu " +
                    "WHERE ptsc.MaSuatChieu = :maSuatChieu";
                
                // *** FIX: Cast về SQLQuery và add scalar ***
                org.hibernate.SQLQuery phuThuQuery = dbSession.createSQLQuery(phuThuSql);
                phuThuQuery.setParameter("maSuatChieu", maSuatChieu);
                
                phuThuQuery.addScalar("MaPhuThu", org.hibernate.type.StringType.INSTANCE);
                phuThuQuery.addScalar("TenPhuThu", org.hibernate.type.StringType.INSTANCE);
                phuThuQuery.addScalar("Gia", org.hibernate.type.BigDecimalType.INSTANCE);
                
                List<Object[]> phuThuResults = phuThuQuery.list();
                
                List<PhuThuEntity> phuThuList = new ArrayList<>();
                BigDecimal tongPhuThu = BigDecimal.ZERO;
                
                for (Object[] row : phuThuResults) {
                    PhuThuEntity pt = new PhuThuEntity();
                    pt.setMaPhuThu((String) row[0]);
                    pt.setTenPhuThu((String) row[1]);
                    pt.setGia((BigDecimal) row[2]);
                    
                    phuThuList.add(pt);
                    tongPhuThu = tongPhuThu.add(pt.getGia());
                }
                
                // 8. Đưa dữ liệu vào model
                model.addAttribute("selectedSeats", selectedSeats);
                model.addAttribute("vePrices", vePrices);
                model.addAttribute("selectedCombos", selectedCombos);
                model.addAttribute("comboNames", comboNames);
                model.addAttribute("comboPrices", comboPrices);
                model.addAttribute("selectedBapNuocs", selectedBapNuocs);
                model.addAttribute("bapNuocNames", bapNuocNames);
                model.addAttribute("bapNuocPrices", bapNuocPrices);
                model.addAttribute("phuThuList", phuThuList);
                model.addAttribute("tongPhuThu", tongPhuThu);
                model.addAttribute("tongTien", donHang.getTongTien());
                model.addAttribute("discountAmount", donHang.getGiaTriGiamGia());
                model.addAttribute("appliedPoints", appliedPoints);
                model.addAttribute("pointsDiscount", donHang.tinhGiamGiaTuDiem());
                model.addAttribute("paymentMethod", "Chuyển khoản (Test)");
                
                // 9. Xóa session
                clearSession(session);
                
                System.out.println("✓ BYPASS PAYMENT COMPLETED");
                
                // 10. Return
                return "user/pay-success";
                
            } catch (Exception e) {
                e.printStackTrace();
                model.addAttribute("error", "Lỗi khi xử lý thanh toán bypass: " + e.getMessage());
                return "user/payment";
            }
        }


    

    @Transactional
    @RequestMapping(value = "/momo-payment-return", method = RequestMethod.GET)
    public String momoPaymentReturn(
            @RequestParam(value = "partnerCode", required = false) String partnerCode,
            @RequestParam(value = "orderId", required = false) String orderId,
            @RequestParam(value = "requestId", required = false) String requestId,
            @RequestParam(value = "amount", required = false) Long amount,
            @RequestParam(value = "orderInfo", required = false) String orderInfo,
            @RequestParam(value = "orderType", required = false) String orderType,
            @RequestParam(value = "transId", required = false) Long transId,
            @RequestParam(value = "resultCode", required = false) Integer resultCode,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "payType", required = false) String payType,
            @RequestParam(value = "responseTime", required = false) Long responseTime,
            @RequestParam(value = "extraData", required = false) String extraData,
            @RequestParam(value = "signature", required = false) String signature,
            HttpSession session,
            Model model) {
        
        try {
            System.out.println("=== MoMo Payment Return ===");
            System.out.println("Result Code: " + resultCode);
            System.out.println("Message: " + message);
            System.out.println("Order ID: " + orderId);
            System.out.println("Trans ID: " + transId);
            
            // Kiểm tra kết quả thanh toán
            if (resultCode != null && resultCode == 0) {
                // THANH TOÁN THÀNH CÔNG
                System.out.println("✓ Thanh toán MoMo thành công!");
                
                // Lấy thông tin từ session
                DonHangEntity donHang = (DonHangEntity) session.getAttribute("pendingOrder");
                String selectedSeats = (String) session.getAttribute("selectedSeats");
                String maSuatChieu = (String) session.getAttribute("maSuatChieu");
                Map<String, Integer> selectedCombos = (Map<String, Integer>) session.getAttribute("selectedCombos");
                Map<String, Integer> selectedBapNuocs = (Map<String, Integer>) session.getAttribute("selectedBapNuocs");
                Integer appliedPoints = (Integer) session.getAttribute("appliedPoints");
                KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
                
                if (donHang != null && selectedSeats != null && maSuatChieu != null) {
                    Session dbSession = sessionFactory.getCurrentSession();
                    
                    // 1. Cập nhật trạng thái đơn hàng
                    donHang.setTrangThaiDonHang("Đã thanh toán");
                    dbSession.save(donHang);
                    
                    System.out.println("✓ Đã lưu đơn hàng: " + donHang.getMaDonHang());
                    
                    // *** 2. GÁN VÉ TẠM VÀO ĐƠN HÀNG ***
                    List<String> selectedSeatList = Arrays.asList(selectedSeats.split(","));
                    
                    // Query ghế để lấy thông tin display
                    String sql = "SELECT " +
                                 "g.MaGhe, g.SoGhe, g.TenHang, g.MaLoaiGhe, g.MaPhongChieu, " +
                                 "v.SoGheDisplay, v.TenHangDisplay, " +
                                 "lg.MaLoaiGhe AS LgMaLoaiGhe, CAST(lg.TenLoaiGhe AS VARCHAR(50)) AS TenLoaiGhe, " +
                                 "lg.HeSoGia, lg.MauGhe, lg.SoCho " +
                                 "FROM Ghe g " +
                                 "LEFT JOIN V_GheUserView v ON g.MaGhe = v.MaGhe " +
                                 "LEFT JOIN LoaiGhe lg ON g.MaLoaiGhe = lg.MaLoaiGhe " +
                                 "WHERE g.MaPhongChieu = (SELECT MaPhongChieu FROM SuatChieu WHERE MaSuatChieu = :maSuatChieu)";
                    
                    Query gheQuery = dbSession.createSQLQuery(sql);
                    gheQuery.setParameter("maSuatChieu", maSuatChieu);
                    List<Object[]> gheResults = gheQuery.list();
                    
                    // Map ghế
                    List<GheEntity> gheList = new ArrayList<>();
                    for (Object[] row : gheResults) {
                        GheEntity ghe = new GheEntity();
                        ghe.setMaGhe((String) row[0]);
                        ghe.setSoGheAdmin((String) row[5]);
                        ghe.setTenHangAdmin((String) row[6]);
                        gheList.add(ghe);
                    }
                    
                    // Query vé tạm (chưa có đơn hàng)
                    Query veQuery = dbSession.createQuery(
                    	    "FROM VeEntity v WHERE v.maSuatChieu = :maSuatChieu AND v.donHang IS NULL");
                    	veQuery.setParameter("maSuatChieu", maSuatChieu);
                    	List<VeEntity> pendingVes = veQuery.list();
                    
                    System.out.println("✓ Found " + pendingVes.size() + " pending tickets");
                    
                    // Gán vé vào đơn hàng
                    int updatedCount = 0;
                    for (VeEntity ve : pendingVes) {
                        for (GheEntity ghe : gheList) {
                            if (ghe.getMaGhe().equals(ve.getMaGhe())) {
                                String seatId = ghe.getTenHangAdmin() + ghe.getSoGheAdmin();
                                if (selectedSeatList.contains(seatId)) {
                                    ve.setDonHang(donHang);  // ← GÁN ĐƠN HÀNG
                                    dbSession.update(ve);
                                    break;
                                }
                            }
                        }
                    }
                    
                    System.out.println("✓ Đã gán " + updatedCount + " vé vào đơn hàng");
                    
                    // 3. Lưu chi tiết combo
                    if (selectedCombos != null && !selectedCombos.isEmpty()) {
                        for (Map.Entry<String, Integer> entry : selectedCombos.entrySet()) {
                            ChiTietDonHangComboEntity chiTiet = new ChiTietDonHangComboEntity();
                            chiTiet.setDonHang(donHang);
                            chiTiet.setMaCombo(entry.getKey());
                            chiTiet.setSoLuong(entry.getValue());
                            dbSession.save(chiTiet);
                        }
                        System.out.println("✓ Đã lưu " + selectedCombos.size() + " combo");
                    }
                    
                    // 4. Lưu chi tiết bắp nước
                    if (selectedBapNuocs != null && !selectedBapNuocs.isEmpty()) {
                        for (Map.Entry<String, Integer> entry : selectedBapNuocs.entrySet()) {
                            ChiTietDonHangBapNuocEntity chiTiet = new ChiTietDonHangBapNuocEntity();
                            chiTiet.setDonHang(donHang);
                            chiTiet.setMaBapNuoc(entry.getKey());
                            chiTiet.setSoLuong(entry.getValue());
                            dbSession.save(chiTiet);
                        }
                        System.out.println("✓ Đã lưu " + selectedBapNuocs.size() + " bắp nước");
                    }
                    
                    // 5. Lưu thông tin thanh toán
                    ThanhToanEntity thanhToan = new ThanhToanEntity();
                    thanhToan.setMaThanhToan("TT" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8));
                    thanhToan.setDonHang(donHang);
                    thanhToan.setPhuongThuc("MoMo");
                    thanhToan.setSoTien(donHang.getTongTien());
                    thanhToan.setNgayThanhToan(new Date());
                    thanhToan.setTrangThai("Success");
                    dbSession.save(thanhToan);
                    
                    System.out.println("✓ Đã lưu thông tin thanh toán MoMo");
                    
                    // 6. Cập nhật điểm tích lũy
                    if (loggedInUser != null) {
                        KhachHangEntity khachHang = (KhachHangEntity) dbSession.get(
                            KhachHangEntity.class, loggedInUser.getMaKhachHang());
                        
                        if (khachHang != null) {
                            // Trừ điểm đã sử dụng
                            if (appliedPoints != null && appliedPoints > 0) {
                                int currentPoints = khachHang.getTongDiem();
                                khachHang.setTongDiem(currentPoints - appliedPoints);
                                System.out.println("  - Trừ " + appliedPoints + " điểm đã dùng");
                            }
                            
                            // Cộng điểm mới
                            int earnedPoints = donHang.tinhDiem();
                            if (earnedPoints > 0) {
                                khachHang.congDiem(earnedPoints);
                                System.out.println("  + Cộng " + earnedPoints + " điểm mới");
                            }
                            
                            dbSession.update(khachHang);
                            
                            // Cập nhật session
                            loggedInUser.setTongDiem(khachHang.getTongDiem());
                            session.setAttribute("loggedInUser", loggedInUser);
                        }
                    }
                    
                    // 7. Xóa thông tin trong session
                    session.removeAttribute("pendingOrder");
                    session.removeAttribute("pendingMaDonHang");
                    session.removeAttribute("selectedSeats");
                    session.removeAttribute("selectedCombos");
                    session.removeAttribute("selectedBapNuocs");
                    session.removeAttribute("tongTien");
                    session.removeAttribute("maKhuyenMai");
                    session.removeAttribute("discountAmount");
                    session.removeAttribute("appliedPoints");
                    session.removeAttribute("pointsDiscount");
                    session.removeAttribute("momoPayUrl");
                    session.removeAttribute("maSuatChieu");
                    session.removeAttribute("maPhim");
                    session.removeAttribute("vePrices");
                    session.removeAttribute("comboPrices");
                    session.removeAttribute("bapNuocPrices");
                    
                    System.out.println("✓ Đã xóa session data");
                    
                    // 8. Chuyển đến trang thành công
                    model.addAttribute("message", "Thanh toán thành công!");
                    model.addAttribute("orderId", orderId);
                    model.addAttribute("transId", transId);
                    model.addAttribute("donHang", new DonHangModel(donHang));
                    
                    return "redirect:/booking/success?orderId=" + orderId;
                } else {
                    System.err.println("✗ Missing data: donHang=" + donHang + 
                                     ", selectedSeats=" + selectedSeats + 
                                     ", maSuatChieu=" + maSuatChieu);
                    model.addAttribute("error", "Không tìm thấy thông tin đơn hàng. Vui lòng liên hệ hỗ trợ.");
                    return "user/payment";
                }
                
            } else {
                // THANH TOÁN THẤT BẠI
                System.err.println("✗ Thanh toán MoMo thất bại: " + message);
                
                DonHangEntity donHang = (DonHangEntity) session.getAttribute("pendingOrder");
                if (donHang != null) {
                    Session dbSession = sessionFactory.getCurrentSession();
                    donHang.setTrangThaiDonHang("Đã hủy");
                    dbSession.save(donHang);
                    
                    System.out.println("✓ Đã đánh dấu đơn hàng " + donHang.getMaDonHang() + " là 'Đã hủy'");
                }
                
                model.addAttribute("error", "Thanh toán thất bại: " + message);
                return "user/payment";
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("✗ Exception trong momoPaymentReturn: " + e.getMessage());
            model.addAttribute("error", "Lỗi xử lý callback từ MoMo: " + e.getMessage());
            return "user/payment";
        }
    }



    @Transactional
    @RequestMapping(value = "/momo-notify", method = RequestMethod.POST)
    public void momoNotify(@RequestParam Map<String, String> params) {
        try {
            // Verify signature
            String rawData = String.format(
                    "partnerCode=%s&accessKey=%s&requestId=%s&amount=%s&orderId=%s&orderInfo=%s&orderType=%s&transId=%s&message=%s&localMessage=%s&responseTime=%s&errorCode=%s&payType=%s&extraData=%s",
                    params.get("partnerCode"),
                    MomoConfig.momo_AccessKey,
                    params.get("requestId"),
                    params.get("amount"),
                    params.get("orderId"),
                    params.get("orderInfo"),
                    params.get("orderType"),
                    params.get("transId"),
                    params.get("message"),
                    params.get("localMessage"),
                    params.get("responseTime"),
                    params.get("errorCode"),
                    params.get("payType"),
                    params.get("extraData")
            );
            String computedSignature = computeHmacSha256(rawData, MomoConfig.momo_SecretKey);
            
            if (computedSignature.equals(params.get("signature"))) {
                // Update order status
                Session dbSession = sessionFactory.getCurrentSession();
                Query donHangQuery = dbSession.createQuery("FROM DonHangEntity dh WHERE dh.maDonHang = :maDonHang");
                donHangQuery.setParameter("maDonHang", params.get("orderId"));
                DonHangEntity order = (DonHangEntity) donHangQuery.uniqueResult();
                
                if (order != null) {
                    // *** ĐỔI: Cập nhật trạng thái theo errorCode ***
                    if ("0".equals(params.get("errorCode"))) {
                        order.setTrangThaiDonHang("Đã thanh toán");  // Thay vì setDatHang(true)
                    } else {
                        order.setTrangThaiDonHang("Đã hủy");  // Thay vì setDatHang(false)
                    }
                    dbSession.update(order);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    
  
    @Transactional
    @RequestMapping(value = "/confirm-booking", method = RequestMethod.POST)
    public String confirmBooking(@RequestParam("maPhim") String maPhim,
            @RequestParam("maSuatChieu") String maSuatChieu,
            @RequestParam(value = "selectedSeats", required = false) String selectedSeats,
            HttpSession session,
            Model model) {
        return "forward:/booking/update-seats";
    }

    @Transactional
    @RequestMapping(value = "/confirm-all", method = RequestMethod.POST)
    public String confirmAll(@RequestParam("maPhim") String maPhim,
            @RequestParam("maSuatChieu") String maSuatChieu,
            @RequestParam("selectedSeats") String selectedSeats,
            @RequestParam Map<String, String> allParams,
            HttpSession session,
            Model model) {
        return "forward:/booking/select-payment";
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-FORWARDED-FOR");
        return (ip == null || ip.isEmpty()) ? request.getRemoteAddr() : ip;
    }

    public static String computeHmacSha256(String data, String key) throws Exception {
        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
        sha256_HMAC.init(secret_key);
        byte[] hash = sha256_HMAC.doFinal(data.getBytes("UTF-8"));
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private void clearSession(HttpSession session) {
        session.removeAttribute("selectedSeats");
        session.removeAttribute("maPhim");
        session.removeAttribute("maSuatChieu");
        session.removeAttribute("reservationStartTime");
        session.removeAttribute("selectedCombos");
        session.removeAttribute("selectedBapNuocs");
        session.removeAttribute("vePrices");
        session.removeAttribute("comboPrices");
        session.removeAttribute("bapNuocPrices");
        session.removeAttribute("tongTien");
        session.removeAttribute("originTongTien");
        session.removeAttribute("appliedPromoCode");
        session.removeAttribute("discountAmount");
        session.removeAttribute("maKhuyenMai");
        session.removeAttribute("pendingOrder");
        session.removeAttribute("redirectMaPhim");
        session.removeAttribute("redirectMaSuatChieu");
        session.removeAttribute("singleSeats");
        session.removeAttribute("doubleSeats");
        session.removeAttribute("appliedPoints");
        session.removeAttribute("pointsDiscount");
    }

}