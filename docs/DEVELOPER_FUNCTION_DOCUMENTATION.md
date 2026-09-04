# Quy ước mô tả hàm

Từ phiên bản này, mỗi hàm mới hoặc hàm được sửa trong backend, frontend và chatbot phải có mô tả ngắn ngay phía trên hàm.

Mô tả cần trả lời ba ý:

1. Hàm làm nhiệm vụ gì và phục vụ màn hình/API nào.
2. Dữ liệu đầu vào, đầu ra và trạng thái quan trọng.
3. Quy tắc nghiệp vụ hoặc tác dụng phụ cần giữ khi thay đổi.

Backend Java dùng Javadoc (`/** ... */`), frontend TypeScript dùng TSDoc (`/** ... */`), còn SQL migration dùng comment đầu file và comment trước mỗi khối ghi dữ liệu. Hàm private cũng phải được mô tả nếu có logic nghiệp vụ, truy vấn dữ liệu, chuyển trạng thái hoặc gọi dịch vụ bên ngoài.

Ví dụ:

```java
/** Reads the active registration phase for catalog and eligibility screens. */
private Map<String, Object> openReadRound(String semesterId) { ... }
```

Không đưa thông tin bí mật, token, email cá nhân hoặc dữ liệu sinh viên thật vào phần mô tả.
