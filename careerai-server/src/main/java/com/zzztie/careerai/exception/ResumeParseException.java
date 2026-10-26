package com.zzztie.careerai.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(
        value = HttpStatus.BAD_GATEWAY,
        reason = "AI简历解析失败，请稍后重试"
)
public class ResumeParseException extends RuntimeException{
    public ResumeParseException(Throwable cause){
        super("AI简历解析失败",cause);
    }
}
