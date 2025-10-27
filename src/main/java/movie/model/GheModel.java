package movie.model;

public class GheModel {
    private String maGhe;
    private String soGhe;              // Physical: C1, C2 (admin dùng)
    private String tenHang;            // Physical: C, D, E (admin dùng)
    private String soGheAdmin;         // Display: A1, A2 (user xem) ← THÊM
    private String tenHangAdmin;       // Display: A, B, C (user xem) ← THÊM
    private String maLoaiGhe;
    private String maPhongChieu;
    private String mauGhe;

    // Constructors
    public GheModel() {}

    public GheModel(movie.entity.GheEntity entity) {
        if (entity != null) {
            this.maGhe = entity.getMaGhe();
            this.soGhe = entity.getSoGhe();
            this.tenHang = entity.getTenHang();
            
            // *** THÊM 2 DÒNG NÀY - QUAN TRỌNG! ***
            this.soGheAdmin = entity.getSoGheAdmin();    // Từ @Transient field
            this.tenHangAdmin = entity.getTenHangAdmin(); // Từ @Transient field
            
            this.maLoaiGhe = entity.getLoaiGhe() != null ? entity.getLoaiGhe().getMaLoaiGhe() : null;
            this.maPhongChieu = entity.getPhongChieu() != null ? entity.getPhongChieu().getMaPhongChieu() : null;
            this.mauGhe = entity.getLoaiGhe() != null ? entity.getLoaiGhe().getMauGhe() : null;
        }
    }

    // Getters và Setters
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
    
    // *** THÊM GETTER/SETTER CHO DISPLAY FIELDS ***
    public String getSoGheAdmin() { 
        return soGheAdmin; 
    }
    
    public void setSoGheAdmin(String soGheAdmin) { 
        this.soGheAdmin = soGheAdmin; 
    }
    
    public String getTenHangAdmin() { 
        return tenHangAdmin; 
    }
    
    public void setTenHangAdmin(String tenHangAdmin) { 
        this.tenHangAdmin = tenHangAdmin; 
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
    
    public String getMauGhe() { 
        return mauGhe; 
    }
    
    public void setMauGhe(String mauGhe) { 
        this.mauGhe = mauGhe; 
    }
}
