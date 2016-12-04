package com.dempe.forest.client.proxy;

import com.dempe.forest.RefConfMapping;
import com.dempe.forest.ReferConfig;
import com.dempe.forest.codec.Header;
import com.dempe.forest.codec.Message;
import com.dempe.forest.codec.Response;
import com.dempe.forest.codec.compress.Compress;
import com.dempe.forest.codec.serialize.Serialization;
import com.dempe.forest.core.CompressType;
import com.dempe.forest.core.SerializeType;
import com.dempe.forest.core.annotation.MethodProvider;
import com.dempe.forest.transport.NettyResponseFuture;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Created with IntelliJ IDEA.
 * User: Dempe
 * Date: 2016/12/3 0003
 * Time: 下午 5:36
 * To change this template use File | Settings | File Templates.
 */
public class ReferInvocationHandler implements InvocationHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(ReferInvocationHandler.class);

    private final static AtomicLong id = new AtomicLong(0);
    private RefConfMapping mapping;
    private String serviceName;

    public ReferInvocationHandler(RefConfMapping mapping, String serviceName) {
        this.serviceName = serviceName;
        this.mapping = mapping;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        MethodProvider methodProvider = method.getAnnotation(MethodProvider.class);
        if (methodProvider == null) {
            LOGGER.info("method:{} cannot find methodProvider.", method.getName());
            return null;
        }
        String methodName = Strings.isNullOrEmpty(methodProvider.methodName()) ? method.getName() : methodProvider.methodName();
        ReferConfig refConf = mapping.getRefConf(serviceName, methodName);
        if (refConf == null) {
            LOGGER.info("serviceName:{},methodName is not found", serviceName, methodName);
            return null;
        }
        Header header = refConf.makeHeader();
        header.setMessageID(id.incrementAndGet());
        Message message = new Message();
        message.setHeader(header);
        Compress compress = CompressType.getCompressTypeByValueByExtend(header.getExtend());
        Serialization serialization = SerializeType.getSerializationByExtend(header.getExtend());
        byte[] serialize = serialization.serialize(args);
        message.setPayload(compress.compress(serialize));
        NettyResponseFuture<Response> responseFuture = refConf.getPool().write(message, header.getTimeOut());
        return responseFuture.getPromise().await().getResult();
    }


}


