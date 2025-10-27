package movie.entity;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Entity
@Table(name = "Ve")
public class VeEntity {
    @Id
    @Column(name = "MaVe", length = 10)
    private String maVe;

    @Column(name = "MaSuatChieu", length = 10)
    private String maSuatChieu;

    @Column(name = "MaGhe", length = 10)
    private String maGhe;

    // *** XÓA 2 DÒNG NÀY ***
    // @Column(name = "MaKhachHang", length = 10)
    // private String maKhachHang;

    @Column(name = "GiaVe")
    private BigDecimal giaVe;

    @Column(name = "NgayMua")
    @Temporal(TemporalType.TIMESTAMP)
    private Date ngayMua;

    @ManyToOne
    @JoinColumn(name = "MaDonHang", referencedColumnName = "MaDonHang")
    private DonHangEntity donHang;

    @ManyToOne
    @JoinColumn(name = "MaGhe", referencedColumnName = "MaGhe", insertable = false, updatable = false)
    private GheEntity ghe;

    @ManyToOne
    @JoinColumn(name = "MaSuatChieu", referencedColumnName = "MaSuatChieu", insertable = false, updatable = false)
    private SuatChieuEntity suatChieu;

    // *** THÊM: Helper method để lấy khách hàng ***
    @Transient
    public String getMaKhachHang() {
        return (donHang != null) ? donHang.getMaKhachHang() : null;
    }

    // Getters và Setters (XÓA setter cho maKhachHang)
    public String getMaVe() { return maVe; }
    public void setMaVe(String maVe) { this.maVe = maVe; }
    
    public String getMaSuatChieu() { return maSuatChieu; }
    public void setMaSuatChieu(String maSuatChieu) { this.maSuatChieu = maSuatChieu; }
    
    public String getMaGhe() { return maGhe; }
    public void setMaGhe(String maGhe) { this.maGhe = maGhe; }
    
    // *** XÓA setter cho maKhachHang ***
    // public void setMaKhachHang(String maKhachHang) { this.maKhachHang = maKhachHang; }
    
    public BigDecimal getGiaVe() { return giaVe; }
    public void setGiaVe(BigDecimal giaVe) { this.giaVe = giaVe; }
    
    public Date getNgayMua() { return ngayMua; }
    public void setNgayMua(Date ngayMua) { this.ngayMua = ngayMua; }
    
    public DonHangEntity getDonHang() { return donHang; }
    public void setDonHang(DonHangEntity donHang) { this.donHang = donHang; }
    
    public GheEntity getGhe() { return ghe; }
    public void setGhe(GheEntity ghe) { this.ghe = ghe; }
    
    public SuatChieuEntity getSuatChieu() { return suatChieu; }
    public void setSuatChieu(SuatChieuEntity suatChieu) { this.suatChieu = suatChieu; }
}
