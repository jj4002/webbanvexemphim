<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>Giao dịch đang chờ - Galaxy Cinema</title>
    <style>
        .warning-container {
            max-width: 600px;
            margin: 100px auto;
            padding: 40px;
            text-align: center;
            background: #fff3cd;
            border: 2px solid #ffc107;
            border-radius: 12px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.1);
        }
        .warning-container h2 {
            color: #856404;
            margin-bottom: 20px;
        }
        .warning-container p {
            font-size: 16px;
            color: #856404;
            margin: 15px 0;
        }
        .warning-container .actions {
            margin-top: 30px;
            display: flex;
            gap: 15px;
            justify-content: center;
        }
        .warning-container .btn {
            padding: 12px 24px;
            border-radius: 8px;
            text-decoration: none;
            font-weight: 500;
            transition: all 0.3s ease;
        }
        .btn-primary {
            background: #ff5722;
            color: white;
        }
        .btn-primary:hover {
            background: #e64a19;
        }
        .btn-secondary {
            background: #6c757d;
            color: white;
        }
        .btn-secondary:hover {
            background: #5a6268;
        }
    </style>
</head>
<body>
    <div class="warning-container">
        <h2>⚠️ Bạn đang có giao dịch chưa hoàn thành</h2>
        <p>${error}</p>
        <p>Vui lòng hoàn tất giao dịch hiện tại hoặc đợi hết thời gian giữ ghế (5 phút).</p>
        
        <div class="actions">
            <a href="${pageContext.request.contextPath}/booking/select-seats?maPhim=${maPhim}&maSuatChieu=${pendingSuatChieu}" 
               class="btn btn-primary">
                Quay lại giao dịch đang chờ
            </a>
            <a href="${pageContext.request.contextPath}/home/" 
               class="btn btn-secondary">
                Về trang chủ
            </a>
        </div>
    </div>
</body>
</html>
