<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <link rel="stylesheet" href="${pageContext.request.contextPath}/resources/user/css/styles.css?v=1.2" />
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.4.0/css/all.min.css">
    <title>Hồ Sơ - Galaxy Cinema</title>
    <style>
        * { margin: 0; padding: 0; box-sizing: border-box; }
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f7fa; }
        
        .container { max-width: 1400px; margin: 2rem auto; padding: 0 2rem; }
        
        /* Alert Messages */
        .alert { padding: 1rem; border-radius: 8px; margin-bottom: 1.5rem; display: flex; align-items: center; gap: 0.75rem; animation: slideDown 0.3s ease; }
        .alert-success { background: #d4edda; color: #155724; border: 1px solid #c3e6cb; }
        .alert-error { background: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }
        .alert i { font-size: 1.25rem; }
        
        @keyframes slideDown { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
        
        /* Layout */
        .profile-grid { display: grid; grid-template-columns: 350px 1fr; gap: 2rem; margin-top: 2rem; }
        
        /* User Card */
        .user-card { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); border-radius: 16px; padding: 2rem; color: white; box-shadow: 0 10px 30px rgba(0,0,0,0.2); }
        .user-avatar { width: 120px; height: 120px; border-radius: 50%; background: white; display: flex; align-items: center; justify-content: center; margin: 0 auto 1.5rem; font-size: 3rem; color: #667eea; font-weight: bold; box-shadow: 0 5px 15px rgba(0,0,0,0.2); }
        .user-name { text-align: center; font-size: 1.5rem; font-weight: bold; margin-bottom: 0.5rem; }
        .user-email { text-align: center; opacity: 0.9; margin-bottom: 1.5rem; }
        .user-stats { background: rgba(255,255,255,0.15); border-radius: 12px; padding: 1.5rem; margin-top: 1.5rem; backdrop-filter: blur(10px); }
        .stat-item { display: flex; justify-content: space-between; padding: 0.75rem 0; border-bottom: 1px solid rgba(255,255,255,0.2); }
        .stat-item:last-child { border-bottom: none; }
        .stat-label { opacity: 0.9; }
        .stat-value { font-weight: bold; font-size: 1.1rem; }
        .btn-change-password { width: 100%; background: white; color: #667eea; padding: 0.75rem; border: none; border-radius: 8px; font-weight: 600; cursor: pointer; margin-top: 1rem; transition: transform 0.2s; }
        .btn-change-password:hover { transform: translateY(-2px); box-shadow: 0 5px 15px rgba(0,0,0,0.2); }
        
        /* Order History Card */
        .order-card { background: white; border-radius: 16px; padding: 2rem; box-shadow: 0 2px 15px rgba(0,0,0,0.08); }
        .order-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 2rem; padding-bottom: 1rem; border-bottom: 3px solid #ff5722; }
        .order-title { font-size: 1.75rem; font-weight: bold; color: #2d3748; display: flex; align-items: center; gap: 0.75rem; }
        .order-count { background: #ff5722; color: white; padding: 0.25rem 0.75rem; border-radius: 20px; font-size: 0.9rem; }
        
        /* Filter Tabs */
        .filter-tabs { display: flex; gap: 1rem; margin-bottom: 1.5rem; flex-wrap: wrap; }
        .tab { padding: 0.75rem 1.5rem; border-radius: 8px; border: 2px solid #e2e8f0; background: white; cursor: pointer; transition: all 0.3s; font-weight: 500; }
        .tab:hover { border-color: #ff5722; color: #ff5722; }
        .tab.active { background: #ff5722; color: white; border-color: #ff5722; }
        
        /* Order Table */
        .order-table { width: 100%; border-collapse: separate; border-spacing: 0 0.75rem; }
        .order-table thead th { text-align: left; padding: 1rem; background: #f7fafc; color: #4a5568; font-weight: 600; font-size: 0.9rem; text-transform: uppercase; letter-spacing: 0.5px; }
        .order-table tbody tr { background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.05); transition: transform 0.2s, box-shadow 0.2s; cursor: pointer; }
        .order-table tbody tr:hover { transform: translateY(-2px); box-shadow: 0 4px 15px rgba(0,0,0,0.1); }
        .order-table tbody td { padding: 1.25rem 1rem; border-top: 1px solid #f7fafc; border-bottom: 1px solid #f7fafc; }
        .order-table tbody td:first-child { border-left: 1px solid #f7fafc; border-radius: 8px 0 0 8px; }
        .order-table tbody td:last-child { border-right: 1px solid #f7fafc; border-radius: 0 8px 8px 0; }
        
        /* Status Badge */
        .status-badge { display: inline-block; padding: 0.4rem 1rem; border-radius: 20px; font-size: 0.85rem; font-weight: 600; }
        .status-paid { background: #d4edda; color: #155724; }
        .status-pending { background: #fff3cd; color: #856404; }
        .status-cancelled { background: #f8d7da; color: #721c24; }
        
        /* Action Buttons */
        .action-buttons { display: flex; gap: 0.5rem; }
        .btn-action { padding: 0.5rem 1rem; border: none; border-radius: 6px; cursor: pointer; font-weight: 500; transition: all 0.2s; display: flex; align-items: center; gap: 0.5rem; }
        .btn-detail { background: #3182ce; color: white; }
        .btn-detail:hover { background: #2c5282; }
        .btn-cancel { background: #e53e3e; color: white; }
        .btn-cancel:hover { background: #c53030; }
        .btn-cancel:disabled { background: #cbd5e0; cursor: not-allowed; }
        
        /* Modal */
        .modal { display: none; position: fixed; z-index: 1000; left: 0; top: 0; width: 100%; height: 100%; overflow: auto; background: rgba(0,0,0,0.5); backdrop-filter: blur(5px); animation: fadeIn 0.3s; }
        .modal-content { background: white; margin: 3% auto; padding: 0; border-radius: 16px; width: 90%; max-width: 900px; box-shadow: 0 20px 60px rgba(0,0,0,0.3); animation: slideUp 0.3s; }
        .modal-header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 1.5rem 2rem; border-radius: 16px 16px 0 0; display: flex; justify-content: space-between; align-items: center; }
        .modal-body { padding: 2rem; max-height: 70vh; overflow-y: auto; }
        .close { color: white; font-size: 2rem; font-weight: bold; cursor: pointer; transition: transform 0.2s; }
        .close:hover { transform: rotate(90deg); }
        
        @keyframes fadeIn { from { opacity: 0; } to { opacity: 1; } }
        @keyframes slideUp { from { transform: translateY(50px); opacity: 0; } to { transform: translateY(0); opacity: 1; } }
        
        /* Detail Tables */
        .detail-section { margin-bottom: 2rem; }
        .detail-section h3 { color: #2d3748; margin-bottom: 1rem; font-size: 1.25rem; display: flex; align-items: center; gap: 0.5rem; }
        .detail-table { width: 100%; border-collapse: collapse; }
        .detail-table th { background: #f7fafc; padding: 0.75rem; text-align: left; font-weight: 600; border-bottom: 2px solid #e2e8f0; }
        .detail-table td { padding: 0.75rem; border-bottom: 1px solid #e2e8f0; }
        
        /* No Orders */
        .no-orders { text-align: center; padding: 4rem 2rem; }
        .no-orders i { font-size: 4rem; color: #cbd5e0; margin-bottom: 1rem; }
        .no-orders p { color: #718096; font-size: 1.1rem; }
        
        /* Responsive */
        @media (max-width: 1024px) {
            .profile-grid { grid-template-columns: 1fr; }
            .order-table { font-size: 0.9rem; }
            .action-buttons { flex-direction: column; }
        }
        
        /* THÊM: Navbar Styles */
.navbar {
    background: #1a1a2e;
    padding: 1rem 0;
    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
}

.container-nav {
    max-width: 1400px;
    margin: 0 auto;
    padding: 0 2rem;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.navbar-brand .logo {
    color: #ff5722;
    font-size: 1.75rem;
    font-weight: bold;
    text-decoration: none;
    letter-spacing: 1px;
}

.navbar-toggle {
    display: none;
    background: none;
    border: none;
    cursor: pointer;
}

.navbar-toggle span {
    display: block;
    width: 25px;
    height: 3px;
    background: white;
    margin: 5px 0;
    transition: 0.3s;
}

.nav-links {
    display: flex;
    list-style: none;
    gap: 2rem;
    align-items: center;
}

.nav-links a {
    color: white;
    text-decoration: none;
    font-weight: 500;
    transition: color 0.3s;
}

.nav-links a:hover {
    color: #ff5722;
}

.login-btn {
    background: #ff5722;
    padding: 0.5rem 1.5rem;
    border-radius: 6px;
}

/* Footer */
.footer {
    background: #1a1a2e;
    color: white;
    padding: 2rem 0 1rem;
    margin-top: 4rem;
}

.footer-content {
    max-width: 1400px;
    margin: 0 auto;
    padding: 0 2rem;
    text-align: center;
}

.footer-bottom {
    padding-top: 2rem;
    border-top: 1px solid rgba(255,255,255,0.1);
    opacity: 0.7;
}

/* Responsive */
@media (max-width: 768px) {
    .navbar-toggle {
        display: block;
    }
    
    .nav-links {
        position: fixed;
        left: -100%;
        top: 70px;
        flex-direction: column;
        background: #1a1a2e;
        width: 100%;
        padding: 2rem;
        transition: 0.3s;
        box-shadow: 0 10px 30px rgba(0,0,0,0.3);
    }
    
    .nav-links.active {
        left: 0;
    }
}

    </style>
</head>
<body>
    <nav class="navbar">
        <div class="container-nav">
            <div class="navbar-brand">
                <a href="${pageContext.request.contextPath}/home/" class="logo">Galaxy Cinema</a>
            </div>
            <button class="navbar-toggle" aria-label="Toggle navigation">
                <span></span><span></span><span></span>
            </button>
            <ul class="nav-links">
                <li><a href="${pageContext.request.contextPath}/home/">Phim</a></li>
                <c:choose>
                    <c:when test="${not empty sessionScope.loggedInUser}">
                        <li><a href="${pageContext.request.contextPath}/user/profile">Xin chào, ${sessionScope.loggedInUser.tenKhachHang}</a></li>
                        <li><a href="${pageContext.request.contextPath}/auth/logout" class="login-btn">Đăng Xuất</a></li>
                    </c:when>
                    <c:otherwise>
                        <li><a href="${pageContext.request.contextPath}/auth/login" class="login-btn">Đăng Nhập</a></li>
                    </c:otherwise>
                </c:choose>
            </ul>
        </div>
    </nav>

    <div class="container">
        <!-- Alert Messages -->
        <c:if test="${not empty error}">
            <div class="alert alert-error">
                <i class="fas fa-exclamation-circle"></i>
                <span>${error}</span>
            </div>
        </c:if>
        <c:if test="${not empty success}">
            <div class="alert alert-success">
                <i class="fas fa-check-circle"></i>
                <span>${success}</span>
            </div>
        </c:if>

        <div class="profile-grid">
            <!-- User Card -->
            <div class="user-card">
                <div class="user-avatar">
                    ${user.tenKhachHang.substring(0,1).toUpperCase()}
                </div>
                <div class="user-name">${user.hoKhachHang} ${user.tenKhachHang}</div>
                <div class="user-email">${user.email}</div>
                
                <div class="user-stats">
                    <div class="stat-item">
                        <span class="stat-label"><i class="fas fa-phone"></i> Số điện thoại:</span>
                        <span class="stat-value">${user.soDienThoai}</span>
                    </div>
                    <div class="stat-item">
                        <span class="stat-label"><i class="fas fa-star"></i> Điểm tích lũy:</span>
                        <span class="stat-value">${sessionScope.loggedInUser.tongDiem}</span>
                    </div>
                    <div class="stat-item">
                        <span class="stat-label"><i class="fas fa-calendar"></i> Ngày đăng ký:</span>
                        <span class="stat-value"><fmt:formatDate value="${user.ngayDangKy}" pattern="dd/MM/yyyy" /></span>
                    </div>
                    <div class="stat-item">
                        <span class="stat-label"><i class="fas fa-wallet"></i> Tổng chi tiêu 2025:</span>
                        <span class="stat-value"><fmt:formatNumber value="${totalSpending}" pattern="#,###₫" /></span>
                    </div>
                </div>
                
                <button class="btn-change-password" id="changePasswordBtn">
                    <i class="fas fa-key"></i> Đổi mật khẩu
                </button>
            </div>

            <!-- Order History -->
            <div class="order-card">
                <div class="order-header">
                    <div class="order-title">
                        <i class="fas fa-history"></i>
                        Lịch Sử Đơn Hàng
                        <span class="order-count">${fn:length(donHangList)}</span>
                    </div>
                </div>

                <!-- Filter Tabs -->
                <div class="filter-tabs">
                    <button class="tab active" data-filter="all">
                        <i class="fas fa-list"></i> Tất cả
                    </button>
                    <button class="tab" data-filter="Đã thanh toán">
                        <i class="fas fa-check"></i> Đã thanh toán
                    </button>
                    <button class="tab" data-filter="Chờ thanh toán">
                        <i class="fas fa-clock"></i> Chờ thanh toán
                    </button>
                    <button class="tab" data-filter="Đã hủy">
                        <i class="fas fa-times"></i> Đã hủy
                    </button>
                </div>

                <c:choose>
                    <c:when test="${not empty donHangList}">
                        <table class="order-table">
                            <thead>
                                <tr>
                                    <th>Mã Đơn Hàng</th>
                                    <th>Phim</th>
                                    <th>Ngày Đặt</th>
                                    <th>Tổng Tiền</th>
                                    <th>Trạng Thái</th>
                                    <th>Thao Tác</th>
                                </tr>
                            </thead>
                            <tbody>
                                <c:forEach var="donHang" items="${donHangList}">
                                    <tr data-status="${donHang.trangThaiDonHang}">
                                        <td><strong>${donHang.maDonHang}</strong></td>
                                        <td>${donHang.tenPhim}</td>
                                        <td><fmt:formatDate value="${donHang.ngayDat}" pattern="dd/MM/yyyy HH:mm" /></td>
                                        <td><strong><fmt:formatNumber value="${donHang.tongTien}" pattern="#,###₫" /></strong></td>
                                        <td>
                                            <c:choose>
                                                <c:when test="${donHang.trangThaiDonHang == 'Đã thanh toán'}">
                                                    <span class="status-badge status-paid">Đã thanh toán</span>
                                                </c:when>
                                                <c:when test="${donHang.trangThaiDonHang == 'Chờ thanh toán'}">
                                                    <span class="status-badge status-pending">Chờ thanh toán</span>
                                                </c:when>
                                                <c:otherwise>
                                                    <span class="status-badge status-cancelled">Đã hủy</span>
                                                </c:otherwise>
                                            </c:choose>
                                        </td>
                                        <td>
										    <div class="action-buttons">
										        <button class="btn-action btn-detail" 
										                onclick="viewOrderDetails('${donHang.maDonHang}')">
										            <i class="fas fa-eye"></i> Chi tiết
										        </button>
										        
										        <%-- Chỉ hiện nút hủy với đơn "Đã thanh toán" --%>
										        <c:if test="${donHang.trangThaiDonHang == 'Đã thanh toán'}">
										            <button class="btn-action btn-cancel" 
										                    onclick="confirmCancelOrder('${donHang.maDonHang}')">
										                <i class="fas fa-ban"></i> Hủy vé
										            </button>
										        </c:if>
										    </div>
										</td>
                                    </tr>
                                </c:forEach>
                            </tbody>
                        </table>
                    </c:when>
                    <c:otherwise>
                        <div class="no-orders">
                            <i class="fas fa-ticket-alt"></i>
                            <p>Chưa có đơn hàng nào</p>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </div>

    <!-- Change Password Modal -->
    <div id="changePasswordModal" class="modal">
        <div class="modal-content">
            <div class="modal-header">
                <h2><i class="fas fa-key"></i> Đổi Mật Khẩu</h2>
                <span class="close">&times;</span>
            </div>
            <div class="modal-body">
                <form action="${pageContext.request.contextPath}/user/change-password" method="post" class="space-y-4">
                    <div>
                        <label class="block text-sm font-medium mb-2">Mật khẩu hiện tại:</label>
                        <input type="password" name="currentPassword" required class="w-full p-3 border rounded-lg">
                    </div>
                    <div>
                        <label class="block text-sm font-medium mb-2">Mật khẩu mới:</label>
                        <input type="password" name="newPassword" required class="w-full p-3 border rounded-lg">
                    </div>
                    <div>
                        <label class="block text-sm font-medium mb-2">Xác nhận mật khẩu mới:</label>
                        <input type="password" name="confirmPassword" required class="w-full p-3 border rounded-lg">
                    </div>
                    <button type="submit" class="w-full bg-gradient-to-r from-purple-600 to-purple-800 text-white p-3 rounded-lg font-bold hover:shadow-lg">
                        Đổi Mật Khẩu
                    </button>
                </form>
            </div>
        </div>
    </div>

    <!-- Order Details Modal -->
    <div id="orderDetailsModal" class="modal">
        <div class="modal-content">
            <div class="modal-header">
                <h2><i class="fas fa-file-invoice"></i> Chi Tiết Đơn Hàng</h2>
                <span class="close">&times;</span>
            </div>
            <div class="modal-body" id="orderDetailsContent">
                <p class="text-center"><i class="fas fa-spinner fa-spin"></i> Đang tải...</p>
            </div>
        </div>
    </div>

    <footer class="footer">
        <!-- Footer content same as before -->
    </footer>

    <script>
    // Modal Controls
    const changePasswordModal = document.getElementById('changePasswordModal');
    const orderDetailsModal = document.getElementById('orderDetailsModal');
    const changePasswordBtn = document.getElementById('changePasswordBtn');
    const closeButtons = document.getElementsByClassName('close');

    changePasswordBtn.onclick = () => changePasswordModal.style.display = 'block';

    Array.from(closeButtons).forEach(btn => {
        btn.onclick = function() {
            this.closest('.modal').style.display = 'none';
        }
    });

    window.onclick = (e) => {
        if (e.target.classList.contains('modal')) {
            e.target.style.display = 'none';
        }
    };

    // Filter Tabs
    document.querySelectorAll('.tab').forEach(tab => {
        tab.addEventListener('click', function() {
            document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
            this.classList.add('active');
            
            const filter = this.getAttribute('data-filter');
            const rows = document.querySelectorAll('.order-table tbody tr');
            
            rows.forEach(row => {
                if (filter === 'all' || row.getAttribute('data-status') === filter) {
                    row.style.display = '';
                } else {
                    row.style.display = 'none';
                }
            });
        });
    });

    // ✅ Xem chi tiết đơn hàng
    function viewOrderDetails(maDonHang) {
        const modal = document.getElementById('orderDetailsModal');
        const content = document.getElementById('orderDetailsContent');
        
        modal.style.display = 'block';
        content.innerHTML = '<p class="text-center"><i class="fas fa-spinner fa-spin"></i> Đang tải...</p>';

        fetch('${pageContext.request.contextPath}/user/order-details?maDonHang=' + maDonHang)
            .then(res => res.json())
            .then(data => {
                if (data.error) {
                    content.innerHTML = '<p class="text-red-500">' + data.error + '</p>';
                    return;
                }

                let html = '<div class="space-y-4">';
                
                // Vé
                if (data.veList && data.veList.length > 0) {
                    html += '<div class="detail-section">' +
                        '<h3><i class="fas fa-ticket-alt"></i> Vé</h3>' +
                        '<table class="detail-table w-full">' +
                            '<thead><tr>' +
                                '<th>Ghế</th><th>Loại Ghế</th><th>Giá Vé</th>' +
                            '</tr></thead><tbody>';
                    data.veList.forEach(ve => {
                        html += '<tr>' +
                            '<td>' + (ve.soGhe || '') + '</td>' +
                            '<td>' + (ve.tenLoaiGhe || '') + '</td>' +
                            '<td><strong>' + parseFloat(ve.giaVe || 0).toLocaleString('vi-VN') + ' ₫</strong></td>' +
                            '</tr>';
                    });
                    html += '</tbody></table></div>';
                }

                // Combo
                if (data.comboList && data.comboList.length > 0) {
                    html += '<div class="detail-section">' +
                        '<h3><i class="fas fa-box"></i> Combo</h3>' +
                        '<table class="detail-table w-full">' +
                            '<thead><tr>' +
                                '<th>Tên Combo</th><th>Số Lượng</th><th>Giá</th>' +
                            '</tr></thead><tbody>';
                    data.comboList.forEach(combo => {
                        html += '<tr>' +
                            '<td>' + (combo.tenCombo || '') + '</td>' +
                            '<td>' + (combo.soLuong || 0) + '</td>' +
                            '<td><strong>' + parseFloat(combo.giaCombo || 0).toLocaleString('vi-VN') + ' ₫</strong></td>' +
                            '</tr>';
                    });
                    html += '</tbody></table></div>';
                }

                // Bắp Nước
                if (data.bapNuocList && data.bapNuocList.length > 0) {
                    html += '<div class="detail-section">' +
                        '<h3><i class="fas fa-popcorn"></i> Bắp Nước</h3>' +
                        '<table class="detail-table w-full">' +
                            '<thead><tr>' +
                                '<th>Tên</th><th>Số Lượng</th><th>Giá</th>' +
                            '</tr></thead><tbody>';
                    data.bapNuocList.forEach(item => {
                        html += '<tr>' +
                            '<td>' + (item.tenBapNuoc || '') + '</td>' +
                            '<td>' + (item.soLuong || 0) + '</td>' +
                            '<td><strong>' + parseFloat(item.giaBapNuoc || 0).toLocaleString('vi-VN') + ' ₫</strong></td>' +
                            '</tr>';
                    });
                    html += '</tbody></table></div>';
                }

                html += '</div>';
                content.innerHTML = html;
            })
            .catch(err => {
                content.innerHTML = '<p class="text-red-500">Lỗi: ' + err.message + '</p>';
            });
    }

    // ✅ Xác nhận hủy vé
    function confirmCancelOrder(maDonHang) {
        if (confirm(
            '⚠️ XÁC NHẬN HỦY VÉ\n\n' +
            '• Chỉ được hủy trước giờ chiếu ≥ 2 ngày\n' +
            '• Phí hủy: 10%\n' +
            '• Hoàn lại: 90% giá trị đơn hàng\n' +
            '• Hoàn lại điểm đã sử dụng\n\n' +
            'Bạn có chắc muốn hủy vé này?'
        )) {
            const form = document.createElement('form');
            form.method = 'POST';
            form.action = '${pageContext.request.contextPath}/booking/cancel-order';
            
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = 'maDonHang';
            input.value = maDonHang;
            
            form.appendChild(input);
            document.body.appendChild(form);
            form.submit();
        }
    }

    // Navbar Toggle
    const navbarToggle = document.querySelector('.navbar-toggle');
    const navLinks = document.querySelector('.nav-links');

    if (navbarToggle) {
        navbarToggle.addEventListener('click', function() {
            navLinks?.classList.toggle('active');
            this.classList.toggle('open');
        });
    }
</script>

</body>
</html>
