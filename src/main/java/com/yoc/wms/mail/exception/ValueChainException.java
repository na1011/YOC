package com.yoc.wms.mail.exception;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * WMS 통합 예외 (사이드 프로젝트 전용)
 *
 * 운영 환경에서는 기존 통합 패키지의 ValueChainException 사용
 *
 * @author 김찬기
 * @since 1.0
 */
public class ValueChainException extends RuntimeException {

  private String debugMessage;

  /**
   * 에러 메시지만으로 예외 생성
   *
   * @param errMsg 에러 메시지
   */
  public ValueChainException(String errMsg) {
    super(errMsg);
    this.debugMessage = null;
  }

  /**
   * 에러 메시지와 디버그 메시지로 예외 생성
   *
   * @param errMsg 에러 메시지 (사용자에게 노출)
   * @param debMsg 디버그 메시지 (stackTrace 등, null 가능)
   */
  public ValueChainException(String errMsg, String debMsg) {
    super(errMsg);
    this.debugMessage = debMsg;
  }

  /**
   * 에러 메시지와 원인 예외로 예외 생성 (하위 호환)
   *
   * @param message 에러 메시지
   * @param cause 원인 예외
   */
  public ValueChainException(String message, Throwable cause) {
    super(message, cause);
    this.debugMessage = (cause != null) ? getStackTraceString(cause) : null;
  }

  /**
   * 디버그 메시지 조회
   *
   * @return 디버그 메시지 (null 가능)
   */
  public String getDebugMessage() {
    return debugMessage;
  }

  /**
   * Throwable을 stackTrace 문자열로 변환
   *
   * @param t Throwable 객체
   * @return stackTrace 문자열
   */
  private static String getStackTraceString(Throwable t) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    t.printStackTrace(pw);
    return sw.toString();
  }
}