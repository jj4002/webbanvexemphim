package movie.model;

import java.math.BigDecimal;
import java.util.Date;

public class DonHangModel {
    private String maDonHang;
    private String maKhachHang;
    private String maKhuyenMai;
    private String maQuyDoi;
    private BigDecimal tongTien;
    private Date ngayDat;
    private Integer diemSuDung;
    private String trangThaiDonHang;  // *** THÊM DÒNG NÀY ***
    private KhachHangModel khachHang;

    // Constructors
    public DonHangModel() {}

    public DonHangModel(movie.entity.DonHangEntity entity) {
        if (entity != null) {
            this.maDonHang = entity.getMaDonHang();
            this.maKhachHang = entity.getMaKhachHang();
            this.maKhuyenMai = entity.getMaKhuyenMai();
            this.maQuyDoi = entity.getMaQuyDoi();
            this.tongTien = entity.getTongTien();
            this.ngayDat = entity.getNgayDat();
            this.diemSuDung = entity.getDiemSuDung();
            this.trangThaiDonHang = entity.getTrangThaiDonHang();  // *** THÊM DÒNG NÀY ***
        }
    }

    // Getters và Setters
    public String getMaDonHang() { return maDonHang; }
    public void setMaDonHang(String maDonHang) { this.maDonHang = maDonHang; }
    public String getMaKhachHang() { return maKhachHang; }
    public void setMaKhachHang(String maKhachHang) { this.maKhachHang = maKhachHang; }
    public String getMaKhuyenMai() { return maKhuyenMai; }
    public void setMaKhuyenMai(String maKhuyenMai) { this.maKhuyenMai = maKhuyenMai; }
    public String getMaQuyDoi() { return maQuyDoi; }
    public void setMaQuyDoi(String maQuyDoi) { this.maQuyDoi = maQuyDoi; }
    public BigDecimal getTongTien() { return tongTien; }
    public void setTongTien(BigDecimal tongTien) { this.tongTien = tongTien; }
    public Date getNgayDat() { return ngayDat; }
    public void setNgayDat(Date ngayDat) { this.ngayDat = ngayDat; }
    public Integer getDiemSuDung() { return diemSuDung; }
    public void setDiemSuDung(Integer diemSuDung) { this.diemSuDung = diemSuDung; }
    
    // *** THÊM GETTER/SETTER NÀY ***
    public String getTrangThaiDonHang() { return trangThaiDonHang; }
    public void setTrangThaiDonHang(String trangThaiDonHang) { this.trangThaiDonHang = trangThaiDonHang; }
    
    public KhachHangModel getKhachHang() { return khachHang; }
    public void setKhachHang(KhachHangModel khachHang) { this.khachHang = khachHang; }
}
