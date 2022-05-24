package org.ybonfire.netty.common.util;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * 远程调用工具类
 *
 * @author Bo.Yuan5
 * @date 2022-05-19 10:40
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class RemotingUtil {
    /**
     * @description: 将ip:port结构的字符串转换为InetSocketAddress对象
     * @param:
     * @return:
     * @date: 2022/05/19 10:40:35
     */
    public static SocketAddress addressToSocketAddress(final String address) {
        int split = address.lastIndexOf(":");
        String host = address.substring(0, split);
        String port = address.substring(split + 1);
        return new InetSocketAddress(host, Integer.parseInt(port));
    }

    /**
     * @description: 解析连接地址
     * @param:
     * @return:
     * @date: 2022/05/24 14:31:43
     */
    public static String parseChannelAddress(final Channel channel) {
        if (null == channel) {
            return "";
        }
        SocketAddress remote = channel.remoteAddress();
        final String addr = remote != null ? remote.toString() : "";

        if (addr.length() > 0) {
            int index = addr.lastIndexOf("/");
            if (index >= 0) {
                return addr.substring(index + 1);
            }

            return addr;
        }

        return "";
    }

    /**
     * @description: 关闭连接
     * @param:
     * @return:
     * @date: 2022/05/24 14:33:35
     */
    public static void closeChannel(final Channel channel) {
        final String address = parseChannelAddress(channel);
        channel.close().addListener(future -> System.out.println("关闭连接. Address: [" + address + "]. result:" + future.isSuccess()));
    }
}
