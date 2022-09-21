package org.ybonfire.pipeline.server.exception;

import org.ybonfire.pipeline.common.constant.ResponseEnum;

/**
 * 错误请求异常
 *
 * @author yuanbo
 * @date 2022-09-09 14:50
 */
public class BadRequestException extends ServerException {
    private final ResponseEnum responseType = ResponseEnum.BAD_REQUEST;

    public BadRequestException() {}

    public BadRequestException(final String message) {
        super(message);
    }

    public BadRequestException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public BadRequestException(final Throwable cause) {
        super(cause);
    }

    @Override
    public ResponseEnum getResponseType() {
        return responseType;
    }
}
