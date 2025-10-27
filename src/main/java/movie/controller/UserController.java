package movie.controller;

import movie.model.*;
import movie.entity.*;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Query;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/user")
public class UserController {

    @Autowired
    private SessionFactory sessionFactory;

    @RequestMapping(value = "/profile", method = RequestMethod.GET)
    public String showUserProfile(HttpSession session, Model model) {
        KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            model.addAttribute("error", "Vui lòng đăng nhập để xem thông tin cá nhân");
            return "redirect:/auth/login";
        }

        Session dbSession = sessionFactory.openSession();
        try {
            // Lấy đơn hàng với tenPhim
            String sql = "SELECT DISTINCT " +
                         "dh.MaDonHang, dh.NgayDat, dh.TongTien, dh.TrangThaiDonHang, " +
                         "p.TenPhim " +
                         "FROM DonHang dh " +
                         "LEFT JOIN Ve v ON v.MaDonHang = dh.MaDonHang " +
                         "LEFT JOIN SuatChieu sc ON v.MaSuatChieu = sc.MaSuatChieu " +
                         "LEFT JOIN Phim p ON sc.MaPhim = p.MaPhim " +
                         "WHERE dh.MaKhachHang = :maKhachHang " +
                         "ORDER BY dh.NgayDat DESC";
            
            // *** THÊM: Cast về SQLQuery và addScalar ***
            org.hibernate.SQLQuery query = dbSession.createSQLQuery(sql);
            query.setParameter("maKhachHang", loggedInUser.getMaKhachHang());
            
            // *** THÊM: Mapping các cột ***
            query.addScalar("MaDonHang", org.hibernate.type.StringType.INSTANCE);
            query.addScalar("NgayDat", org.hibernate.type.DateType.INSTANCE);
            query.addScalar("TongTien", org.hibernate.type.BigDecimalType.INSTANCE);
            query.addScalar("TrangThaiDonHang", org.hibernate.type.StringType.INSTANCE);
            query.addScalar("TenPhim", org.hibernate.type.StringType.INSTANCE);
            
            List<Object[]> results = query.list();
            
            List<Map<String, Object>> donHangList = new ArrayList<>();
            BigDecimal totalSpending = BigDecimal.ZERO;
            
            for (Object[] row : results) {
                Map<String, Object> donHang = new HashMap<>();
                donHang.put("maDonHang", row[0]);
                donHang.put("ngayDat", row[1]);
                donHang.put("tongTien", row[2]);
                donHang.put("trangThaiDonHang", row[3]);
                donHang.put("tenPhim", row[4] != null ? row[4] : "N/A");
                
                donHangList.add(donHang);
                
                String trangThai = (String) row[3];
                if ("Đã thanh toán".equals(trangThai) || "Hoàn thành".equals(trangThai)) {
                    totalSpending = totalSpending.add((BigDecimal) row[2]);
                }
            }
            
            int progressWidth;
            if (totalSpending.compareTo(new BigDecimal("4000000")) >= 0) {
                progressWidth = 90;
            } else if (totalSpending.compareTo(new BigDecimal("2000000")) >= 0) {
                progressWidth = 45;
            } else {
                progressWidth = totalSpending.divide(new BigDecimal("4000000"), 4, BigDecimal.ROUND_HALF_UP)
                        .multiply(new BigDecimal("90")).intValue();
            }
            
            KhachHangEntity userEntity = (KhachHangEntity) dbSession.get(
                KhachHangEntity.class, loggedInUser.getMaKhachHang());
            
            model.addAttribute("user", userEntity != null ? userEntity : loggedInUser);
            model.addAttribute("donHangList", donHangList);
            model.addAttribute("totalSpending", totalSpending);
            model.addAttribute("progressWidth", progressWidth);
            
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "Lỗi: " + e.getMessage());
        } finally {
            dbSession.close();
        }

        return "user/profile";
    }




    @RequestMapping(value = "/change-password", method = RequestMethod.POST)
    public String changePassword(
            @RequestParam("currentPassword") String currentPassword,
            @RequestParam("newPassword") String newPassword,
            @RequestParam("confirmPassword") String confirmPassword,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            redirectAttributes.addFlashAttribute("error", "Vui lòng đăng nhập để đổi mật khẩu");
            return "redirect:/auth/login";
        }

        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("error", "Mật khẩu mới và xác nhận mật khẩu không khớp");
            return "redirect:/user/profile";
        }

        Session dbSession = sessionFactory.openSession();
        try {
            dbSession.beginTransaction();
            KhachHangEntity user = (KhachHangEntity) dbSession.get(KhachHangEntity.class, loggedInUser.getMaKhachHang());
            if (user == null || !user.getMatKhau().equals(currentPassword)) {
                redirectAttributes.addFlashAttribute("error", "Mật khẩu hiện tại không đúng");
                return "redirect:/user/profile";
            }

            user.setMatKhau(newPassword);
            dbSession.update(user);
            dbSession.getTransaction().commit();
            redirectAttributes.addFlashAttribute("success", "Đổi mật khẩu thành công!");
        } catch (Exception e) {
            if (dbSession.getTransaction() != null) {
                dbSession.getTransaction().rollback();
            }
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Lỗi khi đổi mật khẩu: " + e.getMessage());
        } finally {
            dbSession.close();
        }

        return "redirect:/user/profile";
    }

    @RequestMapping(value = "/order-details", method = RequestMethod.GET)
    @ResponseBody
    public Map<String, Object> getOrderDetails(@RequestParam("maDonHang") String maDonHang, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        KhachHangModel loggedInUser = (KhachHangModel) session.getAttribute("loggedInUser");
        if (loggedInUser == null) {
            response.put("error", "Vui lòng đăng nhập để xem chi tiết đơn hàng");
            return response;
        }

        Session dbSession = sessionFactory.openSession();
        try {
            // Get DonHang
            DonHangEntity donHang = (DonHangEntity) dbSession.get(DonHangEntity.class, maDonHang);
            if (donHang == null || !donHang.getMaKhachHang().equals(loggedInUser.getMaKhachHang())) {
                response.put("error", "Đơn hàng không tồn tại hoặc không thuộc về bạn");
                return response;
            }

            Query veQuery = dbSession.createQuery(
                    "FROM VeEntity v WHERE v.donHang.maDonHang = :maDonHang");
            veQuery.setParameter("maDonHang", maDonHang);
            List<VeEntity> veList = veQuery.list();
            
            List<Map<String, Object>> veDetails = new ArrayList<>();
            for (VeEntity ve : veList) {
                Map<String, Object> veMap = new HashMap<>();
                veMap.put("maVe", ve.getMaVe());
                // *** FIX: Ghép TenHang + SoGhe để có tên đầy đủ ***
                String tenGheDayDu = ve.getGhe().getTenHang() + ve.getGhe().getSoGhe();
                veMap.put("soGhe", tenGheDayDu);  // A1, B9, C2, ...
                veMap.put("tenLoaiGhe", ve.getGhe().getLoaiGhe().getTenLoaiGhe());
                veMap.put("giaVe", ve.getGiaVe());
                veDetails.add(veMap);
            }

            // Get Combo
            Query comboQuery = dbSession.createQuery("FROM ChiTietDonHangComboEntity ctdh WHERE ctdh.donHang.maDonHang = :maDonHang");
            comboQuery.setParameter("maDonHang", maDonHang);
            List<ChiTietDonHangComboEntity> comboEntities = comboQuery.list();
            List<Map<String, Object>> comboDetails = new ArrayList<>();
            for (ChiTietDonHangComboEntity ctdh : comboEntities) {
                ComboEntity combo = (ComboEntity) dbSession.get(ComboEntity.class, ctdh.getMaCombo());
                if (combo != null) {
                    Map<String, Object> comboMap = new HashMap<>();
                    comboMap.put("maCombo", ctdh.getMaCombo());
                    comboMap.put("tenCombo", combo.getTenCombo()); // Tên combo
                    comboMap.put("soLuong", ctdh.getSoLuong()); // Số lượng
                    comboMap.put("giaCombo", combo.getGiaCombo()); // Giá combo
                    comboDetails.add(comboMap);
                }
            }

            // Get BapNuoc
            Query bapNuocQuery = dbSession.createQuery("FROM ChiTietDonHangBapNuocEntity ctdh WHERE ctdh.donHang.maDonHang = :maDonHang");
            bapNuocQuery.setParameter("maDonHang", maDonHang);
            List<ChiTietDonHangBapNuocEntity> bapNuocEntities = bapNuocQuery.list();
            List<Map<String, Object>> bapNuocDetails = new ArrayList<>();
            for (ChiTietDonHangBapNuocEntity ctdh : bapNuocEntities) {
                BapNuocEntity bapNuoc = (BapNuocEntity) dbSession.get(BapNuocEntity.class, ctdh.getMaBapNuoc());
                if (bapNuoc != null) {
                    Map<String, Object> bapNuocMap = new HashMap<>();
                    bapNuocMap.put("maBapNuoc", ctdh.getMaBapNuoc());
                    bapNuocMap.put("tenBapNuoc", bapNuoc.getTenBapNuoc()); // Tên bắp nước
                    bapNuocMap.put("soLuong", ctdh.getSoLuong()); // Số lượng
                    bapNuocMap.put("giaBapNuoc", bapNuoc.getGiaBapNuoc()); // Giá bắp nước
                    bapNuocDetails.add(bapNuocMap);
                }
            }

            response.put("veList", veDetails);
            response.put("comboList", comboDetails);
            response.put("bapNuocList", bapNuocDetails);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("error", "Lỗi khi lấy chi tiết đơn hàng: " + e.getMessage());
        } finally {
            dbSession.close();
        }

        System.out.println("Order details response: " + response);
        return response;
    }
}