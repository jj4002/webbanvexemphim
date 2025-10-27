package movie.service;

import movie.entity.GheEntity;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;

@Service
public class GheService {

    private final SessionFactory sessionFactory;

    @Autowired
    public GheService(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    /**
     * Lấy danh sách ghế với thông tin display (cho user/admin xem)
     * Populate soGheAdmin và tenHangAdmin từ VIEW
     */
    @Transactional(readOnly = true)
    public List<GheEntity> getSeatsWithDisplayInfo(String maPhongChieu) {
        Session session = sessionFactory.getCurrentSession();
        
        // Query từ VIEW để có thông tin display
        String sql = "SELECT " +
                     "g.MaGhe, g.SoGhe, g.TenHang, g.MaLoaiGhe, g.MaPhongChieu, " +
                     "v.SoGheDisplay, v.TenHangDisplay, " +
                     "lg.MauGhe " +
                     "FROM Ghe g " +
                     "LEFT JOIN V_GheUserView v ON g.MaGhe = v.MaGhe " +
                     "LEFT JOIN LoaiGhe lg ON g.MaLoaiGhe = lg.MaLoaiGhe " +
                     "WHERE g.MaPhongChieu = :maPhongChieu " +
                     "ORDER BY ISNULL(v.ThuTuHang, 999), v.SoGheDisplay";
        
        Query query = session.createSQLQuery(sql);
        query.setParameter("maPhongChieu", maPhongChieu);
        List<Object[]> results = query.list();
        
        List<GheEntity> ghes = new ArrayList<>();
        for (Object[] row : results) {
            GheEntity ghe = new GheEntity();
            ghe.setMaGhe((String) row[0]);
            ghe.setSoGhe((String) row[1]);
            ghe.setTenHang((String) row[2]);
            // MaLoaiGhe và MaPhongChieu - để lazy load từ entity
            
            // *** POPULATE @Transient FIELDS ***
            ghe.setSoGheAdmin((String) row[5]);    // Từ VIEW
            ghe.setTenHangAdmin((String) row[6]);  // Từ VIEW
            
            // Set loaiGhe để JSP có thể truy cập mauGhe
            if (row[3] != null) {
                movie.entity.LoaiGheEntity loaiGhe = new movie.entity.LoaiGheEntity();
                loaiGhe.setMaLoaiGhe((String) row[3]);
                if (row[7] != null) {
                    loaiGhe.setMauGhe((String) row[7]);
                }
                ghe.setLoaiGhe(loaiGhe);
            }
            
            ghes.add(ghe);
        }
        
        return ghes;
    }

    /**
     * Lấy ghế theo physical layout (cho admin edit)
     */
    @Transactional(readOnly = true)
    public List<GheEntity> getSeatsPhysical(String maPhongChieu) {
        Session session = sessionFactory.getCurrentSession();
        Query query = session.createQuery(
            "FROM GheEntity g WHERE g.phongChieu.maPhongChieu = :maPhongChieu " +
            "ORDER BY g.tenHang, g.soGhe"
        );
        query.setParameter("maPhongChieu", maPhongChieu);
        return query.list();
    }
}
