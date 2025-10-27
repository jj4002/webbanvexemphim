<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/resources/user/css/styles.css?v=1.2">
    <title>Booking Seat - Galaxy Cinema</title>
    <style>
        .back-btn {
            display: inline-block;
            padding: 10px 20px;
            background-color: #555;
            color: white;
            text-decoration: none;
            border-radius: 5px;
            margin: 10px 0;
        }
        .back-btn:hover {
            background-color: #777;
        }
        .timer-container {
            display: block;
            font-size: 16px;
        }
        #countdown-timer {
            display: inline;
            color: red;
        }
        .booking-container {
            max-width: 1200px;
            margin: 2rem auto;
            display: flex;
            gap: 20px;
        }
        .booking-left, .booking-right {
            
        }
        .movie-info-right {
            background-color: #f5f5f5;
            padding: 1.5rem;
            border-radius: 8px;
            text-align: center;
        }
        .movie-info-right img {
            max-width: 100%;
            height: auto;
            border-radius: 8px;
        }
        .movie-info-right h2 {
            color: #333;
            margin: 1rem 0;
        }
        .movie-info-right p {
            margin: 0.5rem 0;
            color: #666;
        }
        .movie-info-right .price {
            font-size: 1.2rem;
            font-weight: bold;
            color: #ff5722;
        }
        .movie-info-right .booking-summary {
            text-align: left;
            margin-top: 20px;
            padding: 15px;
            background-color: #fff;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        }
        .movie-info-right .booking-summary h3 {
            margin-top: 0;
            color: #333;
        }
        .movie-info-right .booking-summary .selected-seats p,
        .movie-info-right .booking-summary .price-summary p {
            margin: 10px 0;
            color: #666;
        }
        .movie-info-right .booking-summary .confirm-btn {
            width: 100%;
            padding: 10px;
            background-color: #ff5722;
            color: white;
            border: none;
            border-radius: 5px;
            cursor: pointer;
        }
        .movie-info-right .booking-summary .confirm-btn:hover {
            background-color: #e64a19;
        }
        .movie-info-right .booking-summary .confirm-btn:disabled {
            background-color: #ccc;
            cursor: not-allowed;
        }
        .seat-quantity-container {
            margin: 20px 0;
            padding: 15px;
            border: 1px solid #ccc;
            border-radius: 5px;
            display: flex;
            align-items: center;
            gap: 20px;
            flex-wrap: wrap;
        }
        .seat-quantity-container label {
            margin-right: 10px;
            font-weight: bold;
        }
        .seat-quantity {
            display: flex;
            align-items: center;
            gap: 10px;
        }
        .seat-quantity input {
            width: 50px;
            text-align: center;
            padding: 5px;
            border: 1px solid #ccc;
            border-radius: 5px;
        }
        .seat-quantity button {
            padding: 5px 10px;
            background-color: #555;
            color: white;
            border: none;
            border-radius: 5px;
            cursor: pointer;
        }
        .seat-quantity button:hover {
            background-color: #777;
        }
        .seat-quantity button:disabled {
            background-color: #ccc;
            cursor: not-allowed;
        }
	        .seat.locked {
	    cursor: not-allowed;
	    opacity: 0.8;
	    pointer-events: none; /* Không cho click */
	}
	
		.seat.locked::before {
		    content: '🔒';
		    position: absolute;
		    top: 2px;
		    right: 2px;
		    font-size: 10px;
		}
	    .seat {
		    position: relative; /* Để icon lock đặt tuyệt đối */
		}

		.seat.selected {
		    cursor: not-allowed; /* Con trỏ cấm khi hover */
		    opacity: 0.9;
		}
		
		.lock-icon {
		    pointer-events: none; /* Không block click event */
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
                <span></span>
                <span></span>
                <span></span>
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
        <a href="${pageContext.request.contextPath}/home/" class="back-btn">Quay lại</a>
        <c:if test="${not empty sessionScope.selectedSeats}">
            <div class="timer-container">
                <span>Thời gian giữ ghế: </span>
                <span id="countdown-timer"></span>
            </div>
        </c:if>

        <div class="progress-container">
            <div class="progress-step completed" onclick="goToStep(1)">
                <div class="circle">1</div>
                <span>Chọn phim</span>
            </div>
            <div class="progress-step active" onclick="goToStep(2)">
                <div class="circle">2</div>
                <span>Chọn ghế</span>
            </div>
            <div class="progress-step" onclick="goToStep(3)">
                <div class="circle">3</div>
                <span>Chọn đồ ăn</span>
            </div>
            <div class="progress-step" onclick="goToStep(4)">
                <div class="circle">4</div>
                <span>Thanh toán</span>
            </div>
        </div>

        <div class="booking-container">
            <div class="booking-left">
                <c:if test="${not empty error}">
                    <div class="error-message">${error}</div>
                </c:if>
                <c:if test="${not empty success}">
                    <div class="success-message">${success}</div>
                </c:if>

                <div class="seat-quantity-container">
                    <c:forEach var="loaiGhe" items="${loaiGheList}">
                        <div class="seat-quantity">
                            <label for="seatQuantity-${loaiGhe.maLoaiGhe}">${loaiGhe.tenLoaiGhe}:</label>
                            <button type="button" class="decrease-btn" data-ma-loai-ghe="${loaiGhe.maLoaiGhe}">-</button>
                            <input type="number" id="seatQuantity-${loaiGhe.maLoaiGhe}" 
                                   name="seatQuantities[${loaiGhe.maLoaiGhe}]" 
                                   value="<c:out value='${seatQuantities != null && seatQuantities[loaiGhe.maLoaiGhe] != null ? seatQuantities[loaiGhe.maLoaiGhe] : 0}' />" 
                                   min="0" max="4" readonly>
                            <button type="button" class="increase-btn" data-ma-loai-ghe="${loaiGhe.maLoaiGhe}">+</button>
                        </div>
                    </c:forEach>
                </div>

                <div class="screen-container">
                    <div class="screen">SCREEN</div>
                </div>

                <div class="seating-map">
				    <c:forEach var="row" items="${rowLabels}">
				        <div class="seat-row">
				            <span class="row-label">${row}</span>
				            <c:forEach var="ghe" items="${gheList}">
				                <c:if test="${ghe.tenHang eq row}">
				                    <%-- *** SỬA: Kiểm tra null *** --%>
				                    <c:if test="${not empty ghe.tenHang and not empty ghe.soGhe}">
				                        <c:set var="seatId" value="${ghe.tenHang}${ghe.soGhe}" />
				                        <c:set var="isPaid" value="${paidSeats.contains(seatId)}" />
				                        <c:set var="isReserved" value="${reservedSeats.contains(seatId)}" />
				                        <div id="seat-${seatId}" 
				                             class="seat ${ghe.loaiGhe.maLoaiGhe eq 'LG002' ? 'double' : ''} ${isPaid ? 'occupied' : isReserved ? 'reserved' : 'available'}"
				                             data-seat-id="${seatId}" 
				                             data-is-paid="${isPaid}"
				                             data-is-reserved="${isReserved}"
				                             data-he-so-gia="${ghe.loaiGhe.heSoGia}"
				                             data-loai-ghe="${ghe.loaiGhe.maLoaiGhe}"
				                             data-reserve-time="${seatReservationTimes[seatId] != null ? seatReservationTimes[seatId] : ''}"
				                             data-reserved-by="${seatReservedBy[seatId] != null ? seatReservedBy[seatId] : ''}">
				                            ${seatId}
				                            <c:if test="${isReserved}">
				                                <span class="timer" id="timer-${seatId}"></span>
				                            </c:if>
				                        </div>
				                    </c:if>
				                </c:if>
				            </c:forEach>
				        </div>
				    </c:forEach>
				</div>


                <div class="seat-legend">
                    <div class="legend-item">
                        <div class="seat-example available"></div>
                        <span>Ghế đơn trống</span>
                    </div>
                    <div class="legend-item">
                        <div class="seat-example available double"></div>
                        <span>Ghế đôi trống</span>
                    </div>
                    <div class="legend-item">
                        <div class="seat-example selected"></div>
                        <span>Ghế đang chọn</span>
                    </div>
                    <div class="legend-item">
                        <div class="seat-example occupied"></div>
                        <span>Ghế đã đặt</span>
                    </div>
                </div>
            </div>

            <div class="booking-right">
                <div class="movie-info-right">
                    <img src="${pageContext.request.contextPath}/resources/images/${phim.urlPoster}"
                         alt="${phim.tenPhim}"
                         style="width: 200px; height: auto;">
                    <h2>${phim.tenPhim}</h2>
                    <p><strong>Rạp:</strong> ${rapChieu.tenRapChieu} - Rạp ${rapChieu.maRapChieu}</p>
                    <p><strong>Suất:</strong> <fmt:formatDate value="${suatChieu.ngayGioChieu}" pattern="HH:mm" /> - <fmt:formatDate value="${suatChieu.ngayGioChieu}" pattern="dd/MM/yyyy" /></p>
                    <p><strong>Giá ghế đơn:</strong> <span class="price">
                        <fmt:formatNumber value="${phim.giaVe != null ? phim.giaVe : 90000}" pattern="#,###"/>đ
                    </span></p>
                    <div class="booking-summary">
                        <h3>Thông tin đặt vé</h3>
                        <form action="${pageContext.request.contextPath}/booking/reserve-seats" method="post" id="bookingForm">
                            <input type="hidden" name="maPhim" value="${phim.maPhim}">
                            <input type="hidden" name="maSuatChieu" value="${suatChieu.maSuatChieu}">
                            <input type="hidden" name="selectedSeats" id="selected-seats-input">
                            <c:forEach var="loaiGhe" items="${loaiGheList}">
                                <input type="hidden" name="seatQuantities[${loaiGhe.maLoaiGhe}]" 
                                       id="seatQuantityInput-${loaiGhe.maLoaiGhe}" 
                                       value="<c:out value='${seatQuantities != null && seatQuantities[loaiGhe.maLoaiGhe] != null ? seatQuantities[loaiGhe.maLoaiGhe] : 0}' />">
                            </c:forEach>
                            <div class="selected-seats">
                                <p>Ghế đã chọn: <span id="selected-seats-display"></span></p>
                            </div>
                            <div class="price-summary">
                                <p>Tổng tiền: <span id="total-price">0đ</span></p>
                            </div>
                            <button type="submit" class="confirm-btn" id="confirm-btn" disabled>Xác nhận đặt vé</button>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <footer class="footer">
        <div class="footer-content">
            <div class="footer-section">
                <h3>About Galaxy Cinema</h3>
                <p>Your premier theater for the latest movies and entertainment experiences.</p>
            </div>
            <div class="footer-section">
                <h3>Quick Links</h3>
                <ul class="footer-links">
                    <li><a href="#">Now Showing</a></li>
                    <li><a href="#">Coming Soon</a></li>
                    <li><a href="#">Promotions</a></li>
                    <li><a href="#">Gift Cards</a></li>
                </ul>
            </div>
            <div class="footer-section">
                <h3>Connect With Us</h3>
                <div class="social-links">
                    <a href="#">Facebook</a>
                    <a href="#">Twitter</a>
                    <a href="#">Instagram</a>
                    <a href="#">YouTube</a>
                </div>
            </div>
            <div class="footer-section">
                <h3>Newsletter</h3>
                <p>Subscribe for updates and exclusive offers</p>
                <form class="newsletter-form">
                    <input type="email" placeholder="Enter your email">
                    <button type="submit">Subscribe</button>
                </form>
            </div>
        </div>
        <div class="footer-bottom">
            <p>© 2024 Galaxy Cinema. All rights reserved.</p>
        </div>
    </footer>

    <script src="https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js"></script>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js"></script>
    <script>
let selectedSeats = [];
const baseTicketPrice = ${phim.giaVe != null ? phim.giaVe : 90000};
const RESERVATION_TIMEOUT = 5 * 60 * 1000;
const MAX_SEATS = 4;
let timers = {};
let seatQuantities = {};

<c:if test="${not empty loaiGheList}">
    seatQuantities = {
        <c:forEach var="loaiGhe" items="${loaiGheList}" varStatus="status">
            "${loaiGhe.maLoaiGhe}": ${seatQuantities != null && seatQuantities[loaiGhe.maLoaiGhe] != null ? seatQuantities[loaiGhe.maLoaiGhe] : 0}${status.last ? '' : ','}
        </c:forEach>
    };
</c:if>

// ✅ Chỉ 1 DOMContentLoaded duy nhất
document.addEventListener('DOMContentLoaded', function() {
    // Load ghế đã chọn theo user
    const userId = '${sessionScope.loggedInUser.maKhachHang}';
    const suatChieuId = '${suatChieu.maSuatChieu}';
    const savedSeats = sessionStorage.getItem('selectedSeats_' + userId + '_' + suatChieuId);
    
    if (savedSeats) {
        selectedSeats = JSON.parse(savedSeats);
    } else {
        <c:if test="${not empty selectedSeats}">
            selectedSeats = [
                <c:forEach var="seatId" items="${selectedSeats}" varStatus="status">
                    "${seatId}"${status.last ? '' : ','}
                </c:forEach>
            ];
        </c:if>
    }
    
    initializeSeats();
    connectWebSocket();
    startTimers();
    startCountdownTimer();
    updateSummary();
    validateSeatSelection();

    // Event listeners cho + / -
    document.querySelectorAll('.increase-btn').forEach(button => {
        button.addEventListener('click', function() {
            const maLoaiGhe = this.getAttribute('data-ma-loai-ghe');
            increaseSeat('seatQuantity-' + maLoaiGhe, maLoaiGhe);
        });
    });

    document.querySelectorAll('.decrease-btn').forEach(button => {
        button.addEventListener('click', function() {
            const maLoaiGhe = this.getAttribute('data-ma-loai-ghe');
            decreaseSeat('seatQuantity-' + maLoaiGhe, maLoaiGhe);
        });
    });
});

function increaseSeat(inputId, maLoaiGhe) {
    const input = document.getElementById(inputId);
    const max = parseInt(input.getAttribute('max'));
    let value = parseInt(input.value);
    if (value < max) {
        input.value = value + 1;
        seatQuantities[maLoaiGhe] = value + 1;
        document.getElementById('seatQuantityInput-' + maLoaiGhe).value = seatQuantities[maLoaiGhe];
        validateSeatSelection();
    }
}

function decreaseSeat(inputId, maLoaiGhe) {
    const input = document.getElementById(inputId);
    let value = parseInt(input.value);
    if (value > 0) {
        input.value = value - 1;
        seatQuantities[maLoaiGhe] = value - 1;
        document.getElementById('seatQuantityInput-' + maLoaiGhe).value = seatQuantities[maLoaiGhe];
        validateSeatSelection();
    }
}

function validateSeatSelection() {
    let totalSeats = 0;
    for (let maLoaiGhe in seatQuantities) {
        totalSeats += seatQuantities[maLoaiGhe];
    }
    const confirmBtn = document.getElementById('confirm-btn');
    confirmBtn.disabled = totalSeats === 0;
}

function startCountdownTimer() {
    const timerDisplay = document.getElementById('countdown-timer');
    if (!timerDisplay) return;

    const userId = '${sessionScope.loggedInUser.maKhachHang}';
    const suatChieuId = '${suatChieu.maSuatChieu}';
    const storageKey = 'countdownTime_' + userId + '_' + suatChieuId;

    let timeLeft = parseInt(sessionStorage.getItem(storageKey));
    if (!timeLeft) {
        timeLeft = 300;
        sessionStorage.setItem(storageKey, timeLeft);
    }

    function updateTimer() {
        if (timeLeft <= 0) {
            clearInterval(timerInterval);
            alert("⏰ Hết thời gian giữ ghế! Vui lòng chọn lại ghế.");
            sessionStorage.removeItem(storageKey);
            sessionStorage.removeItem('selectedSeats_' + userId + '_' + suatChieuId);
            window.location.href = "${pageContext.request.contextPath}/booking/select-seats?maPhim=${phim.maPhim}&maSuatChieu=${suatChieu.maSuatChieu}";
            return;
        }
        const minutes = Math.floor(timeLeft / 60);
        const seconds = timeLeft % 60;
        timerDisplay.textContent = minutes + ':' + (seconds < 10 ? '0' : '') + seconds;
        timeLeft--;
        sessionStorage.setItem(storageKey, timeLeft);
    }

    updateTimer();
    const timerInterval = setInterval(updateTimer, 1000);

    document.getElementById('bookingForm').addEventListener('submit', function() {
        clearInterval(timerInterval);
        sessionStorage.removeItem(storageKey);
        
        // *** KHÓA TẤT CẢ GHẾ ĐÃ CHỌN ***
        selectedSeats.forEach(seatId => {
            const seatElement = document.getElementById('seat-' + seatId);
            if (seatElement) {
                seatElement.classList.add('locked');
            }
        });
    });
}

function goToStep(step) {
    if (step === 1) {
        window.location.href = "${pageContext.request.contextPath}/movie-detail?id=${phim.maPhim}";
    } else if (step > 2) {
        if (selectedSeats.length === 0) {
            alert("Vui lòng chọn ghế trước khi chuyển sang bước tiếp theo!");
        } else {
            document.getElementById("bookingForm").submit();
        }
    }
}

function initializeSeats() {
    document.querySelectorAll('.seat').forEach(seat => {
        const seatId = seat.getAttribute('data-seat-id');
        const isPaid = seat.getAttribute('data-is-paid') === 'true';
        const isReserved = seat.getAttribute('data-is-reserved') === 'true';
        const reservedBy = seat.getAttribute('data-reserved-by');
        seat.classList.remove('selected', 'available', 'reserved', 'occupied');
        
        if (isPaid) {
            seat.classList.add('occupied');
        } else if (selectedSeats.includes(seatId)) {
            seat.classList.add('selected');
        } else if (isReserved && reservedBy === '${sessionScope.loggedInUser.maKhachHang}') {
            seat.classList.add('selected');
        } else if (isReserved) {
            seat.classList.add('occupied');
        } else {
            seat.classList.add('available');
        }
        
        seat.addEventListener('click', function(e) {
            e.stopPropagation();
            toggleSeatSelection(this, seatId);
        });
    });
    updateSummary();
    validateSeatSelection();
}

function toggleSeatSelection(seatElement, seatId) {
    const isPaid = seatElement.getAttribute('data-is-paid') === 'true';
    const isReserved = seatElement.getAttribute('data-is-reserved') === 'true';
    const reservedBy = seatElement.getAttribute('data-reserved-by');
    const maLoaiGhe = seatElement.getAttribute('data-loai-ghe');
    const userId = '${sessionScope.loggedInUser.maKhachHang}';
    const suatChieuId = '${suatChieu.maSuatChieu}';
    const storageKey = 'selectedSeats_' + userId + '_' + suatChieuId;
    
    let seatCounts = {};
    selectedSeats.forEach(id => {
        const seat = document.getElementById('seat-' + id);
        const loaiGhe = seat.getAttribute('data-loai-ghe');
        seatCounts[loaiGhe] = (seatCounts[loaiGhe] || 0) + 1;
    });

    if (isPaid) {
        alert('⚠️ Ghế này đã được đặt!');
        return;
    }

    if (seatElement.classList.contains('locked')) {
        alert('⚠️ Ghế đã được giữ trong 5 phút!\nBạn không thể bỏ chọn ghế này.');
        return;
    }
    
    const isCurrentUserSeat = selectedSeats.includes(seatId) || (
        isReserved && reservedBy === userId
    );

    if (seatElement.classList.contains('selected')) {
        selectedSeats = selectedSeats.filter(id => id !== seatId);
        seatElement.classList.remove('selected');
        seatElement.classList.add('available');
        seatElement.removeAttribute('data-is-reserved');
        seatElement.removeAttribute('data-reserved-by');
    }
    else if (seatElement.classList.contains('available') || isCurrentUserSeat) {
        const currentCount = seatCounts[maLoaiGhe] || 0;
        const maxCount = seatQuantities[maLoaiGhe] || 0;
        
        if (currentCount >= maxCount) {
            const loaiGheLabel = document.querySelector(`label[for="seatQuantity-${maLoaiGhe}"]`);
            alert('⚠️ Bạn đã chọn đủ số lượng ghế ' + (loaiGheLabel ? loaiGheLabel.textContent : ''));
            return;
        }
        
        selectedSeats.push(seatId);
        seatElement.classList.remove('available', 'reserved');
        seatElement.classList.add('selected');
        seatElement.setAttribute('data-is-reserved', 'true');
        seatElement.setAttribute('data-reserved-by', userId);
    }
    else if (isReserved) {
        alert('⚠️ Ghế này đang được giữ bởi người dùng khác!');
        return;
    }
    
    sessionStorage.setItem(storageKey, JSON.stringify(selectedSeats));
    updateSummary();
    validateSeatSelection();
}

function updateSummary() {
    document.getElementById('selected-seats-display').textContent = selectedSeats.length > 0 ? selectedSeats.join(', ') : 'Chưa chọn';
    document.getElementById('selected-seats-input').value = selectedSeats.join(',');
    let totalPrice = 0;
    selectedSeats.forEach(seatId => {
        const seatElement = document.getElementById('seat-' + seatId);
        if (seatElement) {
            const heSoGia = parseFloat(seatElement.getAttribute('data-he-so-gia') || '1');
            totalPrice += baseTicketPrice * heSoGia;
        }
    });
    document.getElementById('total-price').textContent = totalPrice.toLocaleString('vi-VN') + 'đ';
    document.getElementById('confirm-btn').disabled = selectedSeats.length === 0;
}

function startTimers() {
    document.querySelectorAll('.seat.reserved').forEach(seat => {
        const seatId = seat.getAttribute('data-seat-id');
        const reserveTime = parseInt(seat.getAttribute('data-reserve-time') || '0');
        if (reserveTime && !selectedSeats.includes(seatId)) {
            updateSeatTimer(seatId, reserveTime);
        }
    });
}

function updateSeatTimer(seatId, reserveTime) {
    if (timers[seatId]) clearInterval(timers[seatId]);
    const timerElement = document.getElementById('timer-' + seatId);
    if (!timerElement) return;

    timers[seatId] = setInterval(() => {
        const now = new Date().getTime();
        const elapsed = now - reserveTime;
        const remaining = RESERVATION_TIMEOUT - elapsed;
        if (remaining <= 0) {
            clearInterval(timers[seatId]);
            delete timers[seatId];
            const seatElement = document.getElementById('seat-' + seatId);
            if (!seatElement.classList.contains('selected')) {
                seatElement.classList.remove('reserved');
                seatElement.classList.add('available');
                seatElement.removeAttribute('data-is-reserved');
                seatElement.querySelector('.timer')?.remove();
            }
        } else {
            const minutes = Math.floor(remaining / 60000);
            const seconds = Math.floor((remaining % 60000) / 1000);
            timerElement.textContent = `${minutes}:${seconds < 10 ? '0' : ''}${seconds}`;
        }
    }, 1000);
}

function connectWebSocket() {
    const socket = new SockJS('${pageContext.request.contextPath}/ws');
    const stompClient = Stomp.over(socket);
    stompClient.connect({}, function(frame) {
        stompClient.subscribe('/topic/seats/${suatChieu.maSuatChieu}', function(message) {
            const updatedSeats = JSON.parse(message.body);
            document.querySelectorAll('.seat').forEach(seat => {
                const seatId = seat.getAttribute('data-seat-id');
                const seatInfo = updatedSeats.find(info => info.seatId === seatId);
                if (seatInfo && !seat.classList.contains('selected')) {
                    seat.classList.remove('available');
                    seat.classList.add('occupied');
                    seat.setAttribute('data-is-reserved', 'true');
                    seat.setAttribute('data-reserved-by', seatInfo.maKhachHang);
                    let timer = seat.querySelector('.timer');
                    if (!timer) {
                        timer = document.createElement('span');
                        timer.className = 'timer';
                        timer.id = 'timer-' + seatId;
                        seat.appendChild(timer);
                    }
                    const now = new Date().getTime();
                    seat.setAttribute('data-reserve-time', now);
                    updateSeatTimer(seatId, now);
                }
            });
        });
        stompClient.subscribe('/topic/paid-seats/${suatChieu.maSuatChieu}', function(message) {
            const paidSeats = JSON.parse(message.body);
            document.querySelectorAll('.seat').forEach(seat => {
                const seatId = seat.getAttribute('data-seat-id');
                if (paidSeats.includes(seatId)) {
                    seat.classList.remove('available', 'reserved', 'selected');
                    seat.classList.add('occupied');
                    seat.setAttribute('data-is-paid', 'true');
                    seat.setAttribute('data-is-reserved', 'false');
                    seat.removeAttribute('data-reserved-by');
                    seat.querySelector('.timer')?.remove();
                    selectedSeats = selectedSeats.filter(id => id !== seatId);
                    updateSummary();
                }
            });
        });
        stompClient.subscribe('/topic/expired-seats/${suatChieu.maSuatChieu}', function(message) {
            const expiredSeats = JSON.parse(message.body);
            document.querySelectorAll('.seat').forEach(seat => {
                const seatId = seat.getAttribute('data-seat-id');
                if (expiredSeats.includes(seatId) && !seat.classList.contains('selected')) {
                    seat.classList.remove('reserved');
                    seat.classList.add('available');
                    seat.setAttribute('data-is-reserved', 'false');
                    seat.removeAttribute('data-reserved-by');
                    seat.querySelector('.timer')?.remove();
                }
            });
        });
    }, function(error) {
        console.error('WebSocket connection error:', error);
    });
}
</script>
</body>
</html>