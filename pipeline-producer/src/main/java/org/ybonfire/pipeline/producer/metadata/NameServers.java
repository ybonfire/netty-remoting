package org.ybonfire.pipeline.producer.metadata;

import java.util.List;
import java.util.Optional;

import org.ybonfire.pipeline.common.exception.BaseException;
import org.ybonfire.pipeline.common.exception.ExceptionTypeEnum;
import org.ybonfire.pipeline.common.model.TopicInfo;
import org.ybonfire.pipeline.common.util.ExceptionUtil;
import org.ybonfire.pipeline.producer.client.IRemotingClient;

/**
 * 元数据服务
 *
 * @author Bo.Yuan5
 * @date 2022-06-27 21:46
 */
public class NameServers {
    private final List<String> nameServerAddressList;
    private final IRemotingClient client;

    public NameServers(final List<String> nameServerAddressList, final IRemotingClient client) {
        this.nameServerAddressList = nameServerAddressList;
        this.client = client;
    }

    /**
     * @description: 查询所有Topic信息
     * @param:
     * @return:
     * @date: 2022/06/29 09:55:22
     */
    public List<TopicInfo> selectAllTopicInfo() {
        if (nameServerAddressList.isEmpty()) {
            throw ExceptionUtil.exception(ExceptionTypeEnum.ILLEGAL_ARGUMENT);
        }

        BaseException e = null;
        try {
            for (int i = 0; i < nameServerAddressList.size(); ++i) {
                final String address = nameServerAddressList.get(i);
                return client.selectAllTopicInfo(address, 10 * 1000L);
            }
        } catch (BaseException ex) {
            e = ex;
        }

        throw e == null ? ExceptionUtil.exception(ExceptionTypeEnum.UNKNOWN) : e;
    }

    /**
     * @description: 根据topic名称查询Topic信息
     * @param:
     * @return:
     * @date: 2022/06/27 21:36:35
     */
    public Optional<TopicInfo> selectTopicInfo(final String topic) {
        if (nameServerAddressList.isEmpty()) {
            throw ExceptionUtil.exception(ExceptionTypeEnum.ILLEGAL_ARGUMENT);
        }

        BaseException e = null;
        try {
            for (int i = 0; i < nameServerAddressList.size(); ++i) {
                final String address = nameServerAddressList.get(i);
                return client.selectTopicInfo(topic, address, 10 * 1000L);
            }
        } catch (BaseException ex) {
            e = ex;
        }

        throw e == null ? ExceptionUtil.exception(ExceptionTypeEnum.UNKNOWN) : e;
    }
}
