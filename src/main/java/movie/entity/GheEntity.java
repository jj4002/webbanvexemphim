package movie.entity;

import javax.persistence.*;

@Entity
@Table(name = "Ghe")
public class GheEntity {
    
    // ========== PHYSICAL PROPERTIES (Database columns) ==========
    
    @Id
    @Column(name = "MaGhe", length = 50)
    private String maGhe;
    
    @Column(name = "SoGhe", length = 10)
    private String soGhe;  // Physical seat number (1, 2, 3...)
    
    @Column(name = "TenHang", length = 1)
    private String tenHang;  // Physical row letter (A, B, C...)
    
    @Column(name = "MaLoaiGhe", length = 10)
    private String maLoaiGhe;
    
    @Column(name = "MaPhongChieu", length = 10)
    private String maPhongChieu;
    
    // ========== DISPLAY PROPERTIES (For user view - Transient) ==========
    
    @Transient
    private String soGheAdmin;  // Display seat number for user
    
    @Transient
    private String tenHangAdmin;  // Display row letter for user
    
    // ========== RELATIONSHIPS ==========
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "MaLoaiGhe", insertable = false, updatable = false)
    private LoaiGheEntity loaiGhe;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MaPhongChieu", insertable = false, updatable = false)
    private PhongChieuEntity phongChieu;
    
    // ========== CONSTRUCTORS ==========
    
    public GheEntity() {
    }
    
    public GheEntity(String maGhe, String soGhe, String tenHang) {
        this.maGhe = maGhe;
        this.soGhe = soGhe;
        this.tenHang = tenHang;
    }
    
    // ========== GETTERS & SETTERS - PHYSICAL ==========
    
    public String getMaGhe() {
        return maGhe;
    }
    
    public void setMaGhe(String maGhe) {
        this.maGhe = maGhe;
    }
    
    public String getSoGhe() {
        return soGhe;
    }
    
    public void setSoGhe(String soGhe) {
        this.soGhe = soGhe;
    }
    
    public String getTenHang() {
        return tenHang;
    }
    
    public void setTenHang(String tenHang) {
        this.tenHang = tenHang;
    }
    
    public String getMaLoaiGhe() {
        return maLoaiGhe;
    }
    
    public void setMaLoaiGhe(String maLoaiGhe) {
        this.maLoaiGhe = maLoaiGhe;
    }
    
    public String getMaPhongChieu() {
        return maPhongChieu;
    }
    
    public void setMaPhongChieu(String maPhongChieu) {
        this.maPhongChieu = maPhongChieu;
    }
    
    // ========== GETTERS & SETTERS - DISPLAY ==========
    
    /**
     * Get display seat number (for user view)
     * Falls back to physical seat number if display is not set
     */
    public String getSoGheAdmin() {
        return soGheAdmin != null && !soGheAdmin.isEmpty() ? soGheAdmin : soGhe;
    }
    
    public void setSoGheAdmin(String soGheAdmin) {
        this.soGheAdmin = soGheAdmin;
    }
    
    /**
     * Get display row letter (for user view)
     * Falls back to physical row letter if display is not set
     */
    public String getTenHangAdmin() {
        return tenHangAdmin != null && !tenHangAdmin.isEmpty() ? tenHangAdmin : tenHang;
    }
    
    public void setTenHangAdmin(String tenHangAdmin) {
        this.tenHangAdmin = tenHangAdmin;
    }
    
    // ========== GETTERS & SETTERS - RELATIONSHIPS ==========
    
    public LoaiGheEntity getLoaiGhe() {
        return loaiGhe;
    }
    
    public void setLoaiGhe(LoaiGheEntity loaiGhe) {
        this.loaiGhe = loaiGhe;
    }
    
    public PhongChieuEntity getPhongChieu() {
        return phongChieu;
    }
    
    public void setPhongChieu(PhongChieuEntity phongChieu) {
        this.phongChieu = phongChieu;
    }
    
    // ========== HELPER METHODS ==========
    
    /**
     * Get full display seat ID (e.g., "A1", "B2")
     */
    public String getFullDisplaySeatId() {
        return getTenHangAdmin() + getSoGheAdmin();
    }
    
    /**
     * Get full physical seat ID (e.g., "A1", "B2")
     */
    public String getFullPhysicalSeatId() {
        return tenHang + soGhe;
    }
    
    /**
     * Check if seat number is valid (not null or empty)
     */
    public boolean isValidSeat() {
        return soGhe != null && !soGhe.isEmpty() && 
               tenHang != null && !tenHang.isEmpty();
    }
    
    // ========== OVERRIDE METHODS ==========
    
    @Override
    public String toString() {
        return "GheEntity{" +
                "maGhe='" + maGhe + '\'' +
                ", physical=" + tenHang + soGhe +
                ", display=" + getTenHangAdmin() + getSoGheAdmin() +
                ", loaiGhe=" + (loaiGhe != null ? loaiGhe.getTenLoaiGhe() : "null") +
                '}';
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GheEntity that = (GheEntity) o;
        return maGhe != null && maGhe.equals(that.maGhe);
    }
    
    @Override
    public int hashCode() {
        return maGhe != null ? maGhe.hashCode() : 0;
    }
}
