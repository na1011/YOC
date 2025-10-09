package com.yoc.wms.mail.exception;

/**
 * WMS 통합 예외 (사이드 프로젝트 전용)
 *
 * 운영 환경에서는 기존 통합 패키지의 ValueChainException 사용
 */
public class ValueChainException extends RuntimeException {

  public ValueChainException(String message) {
    super(message);
  }

  public ValueChainException(String message, Throwable cause) {
    super(message, cause);
  }
}