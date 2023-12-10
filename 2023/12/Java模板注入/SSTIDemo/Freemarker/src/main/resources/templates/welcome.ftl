<!DOCTYPE HTML>
<html>
<div th:fragment="header">
    <h3>Spring Boot Web Freemarker Example</h3>
</div>
<div th:fragment="main">
    <span th:text="'Hello, ' + ${message}"></span>
</div>
</html>