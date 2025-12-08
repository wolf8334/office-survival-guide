package com.xhr.springai.officeSurvivalGuide.bean;

public record Result<T>(int code, String msg, T data) {

    public Result(T data) {
        this(200, "success", data);
    }

    // 用法：return Result.success(map);
    public static <T> Result<T> success(T data) {
        return new Result<>(data);
    }

    public static Result<CommonData> success(String msg,String result) {
        return new Result<>(new CommonData(msg,result));
    }

    public static <T> Result<T> error(String msg) {
        return new Result<>(500, msg, null);
    }

    public static Result<CommonData> userNoInput() {
        return new Result<>(new CommonData("","请输入关键词"));
    }
}