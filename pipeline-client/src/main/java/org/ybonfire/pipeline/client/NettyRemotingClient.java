package org.ybonfire.pipeline.client;

import org.ybonfire.pipeline.client.config.NettyClientConfig;
import org.ybonfire.pipeline.client.connection.Connection;
import org.ybonfire.pipeline.client.connection.ConnectionFactory;
import org.ybonfire.pipeline.client.connection.ConnectionManager;
import org.ybonfire.pipeline.client.dispatcher.impl.NettyRemotingResponseDispatcher;
import org.ybonfire.pipeline.client.exception.InvokeExecuteException;
import org.ybonfire.pipeline.client.exception.InvokeInterruptedException;
import org.ybonfire.pipeline.client.exception.ReadTimeoutException;
import org.ybonfire.pipeline.client.exception.UnSupportedRequestTypeException;
import org.ybonfire.pipeline.client.inflight.InflightRequestManager;
import org.ybonfire.pipeline.client.model.RemoteRequestFuture;
import org.ybonfire.pipeline.client.model.RequestTypeEnum;
import org.ybonfire.pipeline.client.processor.IRemotingResponseProcessor;
import org.ybonfire.pipeline.client.thread.ClientChannelEventHandleThreadService;
import org.ybonfire.pipeline.common.callback.IRequestCallback;
import org.ybonfire.pipeline.common.exception.LifeCycleException;
import org.ybonfire.pipeline.common.logger.IInternalLogger;
import org.ybonfire.pipeline.common.logger.impl.SimpleInternalLogger;
import org.ybonfire.pipeline.common.protocol.IRemotingRequest;
import org.ybonfire.pipeline.common.protocol.IRemotingResponse;
import org.ybonfire.pipeline.common.util.AssertUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Netty远程调用客户端
 *
 * @author Bo.Yuan5
 * @date 2022-05-18 15:28
 */
public abstract class NettyRemotingClient implements IRemotingClient<IRemotingResponseProcessor> {
    private static final IInternalLogger LOGGER = new SimpleInternalLogger();
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
    private final ClientChannelEventHandleThreadService channelEventHandleThreadService =
        new ClientChannelEventHandleThreadService();

    protected NettyRemotingClient() {}

    /**
     * @description: 启动客户端
     * @param:
     * @return:
     * @date: 2022/05/18 15:32:53
     */
    @Override
    public void start() {
        if (isStarted.compareAndSet(false, true)) {
            // start channelEventHandleThreadService
            channelEventHandleThreadService.start();

            // start connection factory
            ConnectionFactory.getInstance().start();

            // start inflight requests manager
            InflightRequestManager.getInstance().start();
        }
    }

    /**
     * @description: 判断是否启动
     * @param:
     * @return:
     * @date: 2022/10/12 10:23:37
     */
    @Override
    public boolean isStarted() {
        return isStarted.get();
    }

    /**
     * @description: 关闭客户端
     * @param:
     * @return:
     * @date: 2022/05/18 15:32:56
     */
    @Override
    public void shutdown() {
        if (isStarted.compareAndSet(true, false)) {
            // start inflight requests manager
            InflightRequestManager.getInstance().shutdown();

            // shutdown connection factory
            ConnectionFactory.getInstance().shutdown();

            // stop ChannelEventHandler
            this.channelEventHandleThreadService.stop();

            // remove and close all connection
            ConnectionManager.getInstance().removeAll();
        }
    }

    /**
     * @description: 同步调用
     * @param:
     * @return:
     * @date: 2022/05/19 10:07:08
     */
    @Override
    public IRemotingResponse request(final IRemotingRequest request, final String address, final long timeoutMillis) {
        acquireOK();
        try {
            return doRequest(request, null, address, timeoutMillis, RequestTypeEnum.SYNC);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new InvokeInterruptedException(ex);
        }
    }

    /**
     * @description: 异步调用
     * @param:
     * @return:
     * @date: 2022/05/19 10:07:13
     */
    @Override
    public void requestAsync(final IRemotingRequest request, final String address, final IRequestCallback callback,
        final long timeoutMillis) throws InterruptedException {
        acquireOK();
        doRequest(request, callback, address, timeoutMillis, RequestTypeEnum.ASYNC);
    }

    /**
     * @description: 单向调用
     * @param:
     * @return:
     * @date: 2022/05/19 10:07:18
     */
    @Override
    public void requestOneway(final IRemotingRequest request, final String address) throws InterruptedException {
        acquireOK();
        doRequest(request, null, address, -1L, RequestTypeEnum.ONEWAY);
    }

    /**
     * @description: 注册响应处理器
     * @param:
     * @return:
     * @date: 2022/05/24 00:22:15
     */
    @Override
    public void registerResponseProcessor(final int responseCode, final IRemotingResponseProcessor processor,
        final ExecutorService executor) {
        NettyRemotingResponseDispatcher.getInstance().registerRemotingRequestProcessor(responseCode, processor,
            executor);
    }

    /**
     * @description: 注册远程调用响应处理器
     * @param:
     * @return:
     * @date: 2022/07/01 17:41:06
     */
    protected abstract void registerResponseProcessors();

    /**
     * @description: 构造RemoteRequestFuture
     * @param:
     * @return:
     * @date: 2022/05/19 13:26:46
     */
    private RemoteRequestFuture buildRemoteRequestFuture(final String address, final Connection connection,
        final IRemotingRequest request, final IRequestCallback callback, final long timeoutMillis) {
        return new RemoteRequestFuture(address, connection, request, callback, timeoutMillis);
    }

    /**
     * @description: 确保服务已就绪
     * @param:
     * @return:
     * @date: 2022/05/19 11:49:04
     */
    private void acquireOK() {
        if (!this.isStarted.get()) {
            throw new LifeCycleException();
        }
    }

    /**
     * @description: 执行调用请求
     * @param:
     * @return:
     * @date: 2022/05/19 14:10:22
     */
    private IRemotingResponse doRequest(final IRemotingRequest request, final IRequestCallback callback,
        final String address, final long timeoutMillis, final RequestTypeEnum type) throws InterruptedException {
        final long startTimestamp = System.currentTimeMillis();

        // 参数校验
        AssertUtils.notNull(address);
        AssertUtils.notNull(request);
        AssertUtils.notNull(type);

        // 建立连接
        final NettyClientConfig config = NettyClientConfig.getInstance();
        final long connectionTimeoutMillis = Math.min(config.getConnectTimeoutMillis(), timeoutMillis);
        final Connection connection = ConnectionManager.getInstance().get(address, connectionTimeoutMillis);
        final long remainingTimeoutMillis = timeoutMillis - (System.currentTimeMillis() - startTimestamp);

        // 缓存在途请求
        final RemoteRequestFuture future =
            buildRemoteRequestFuture(address, connection, request, callback, remainingTimeoutMillis);
        InflightRequestManager.getInstance().add(future);

        // 发送请求
        switch (type) {
            case SYNC:
                final IRemotingResponse response = doRequestSync(future, remainingTimeoutMillis);
                return response;
            case ASYNC:
                doRequestAsync(future);
                return null;
            case ONEWAY:
                doRequestOneWay(future);
                return null;
            default:
                throw new UnSupportedRequestTypeException();
        }
    }

    /**
     * @description: 执行同步请求
     * @param:
     * @return:
     * @date: 2022/05/19 15:03:49
     */
    private IRemotingResponse doRequestSync(final RemoteRequestFuture future, final long timeoutMillis)
        throws InterruptedException {
        try {
            // 发送请求
            future.getConnection().write(future.getRequest()).addListener(f -> {
                if (f.isSuccess()) {
                    future.setRequestSuccess(true);
                } else {
                    LOGGER.error(String.format("Failed to send request. id:[%s]", future.getRequest().getId()));
                    future.setRequestSuccess(false);
                    future.setCause(f.cause());
                }
            });

            // 请求失败
            if (!future.isRequestSuccess()) {
                throw new InvokeExecuteException(future.getCause());
            }

            // 请求成功, 等待响应
            final IRemotingResponse response = future.get(timeoutMillis);
            if (response == null) {
                throw new ReadTimeoutException();
            }

            return response;
        } finally {
            // 移除在途请求
            InflightRequestManager.getInstance().remove(future.getRequest().getId());
        }
    }

    /**
     * @description: 执行异步调用
     * @param:
     * @return:
     * @date: 2022/05/19 15:05:58
     */
    private void doRequestAsync(final RemoteRequestFuture future) {
        try {
            // 发送请求
            future.getConnection().write(future.getRequest()).addListener(f -> {
                if (f.isSuccess()) {
                    future.setRequestSuccess(true);
                } else {
                    LOGGER.error(String.format("Failed to send request. id:[%s]", future.getRequest().getId()));
                    future.setRequestSuccess(false);
                    future.setCause(f.cause());
                }
            });

            // 请求失败
            if (!future.isRequestSuccess()) {
                throw new InvokeExecuteException(future.getCause());
            }
        } finally {
            // 移除在途请求
            if (!future.isRequestSuccess()) {
                InflightRequestManager.getInstance().remove(future.getRequest().getId());
            }
        }
    }

    /**
     * @description: 执行单向调用
     * @param:
     * @return:
     * @date: 2022/05/19 15:06:45
     */
    private void doRequestOneWay(final RemoteRequestFuture future) {
        try {
            // 发送请求
            future.getConnection().write(future.getRequest());
        } finally {
            // 移除在途请求
            InflightRequestManager.getInstance().remove(future.getRequest().getId());
        }
    }
}
