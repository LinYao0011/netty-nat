package server.handler;

import core.constant.FrameConstant;
import core.enums.CommandEnum;
import core.utils.BufUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.Server;
import server.group.ServerChannelGroup;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class ProxyServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(ProxyServerHandler.class);

    /**
     * 数据传输时触发
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss SS");
        //收到外部请求先找配对的内容连接
        Channel proxyChannel = ctx.channel();
        if (ServerChannelGroup.channelPairExist(proxyChannel.id())) {
            //已经存在配对，直接进行消息转发
            Channel internalChannel = ServerChannelGroup.getInternalByProxy(proxyChannel.id());
            logger.debug("代理服务发起请求-"+proxyChannel.id()+"-:"+format.format(new Date()));
            if(internalChannel != null && internalChannel.isActive()) {
                byte[] message = new byte[msg.readableBytes()];
                msg.readBytes(message);
                ByteBuf byteBuf = Unpooled.buffer();
                byteBuf.writeBytes(message);
                internalChannel.writeAndFlush(byteBuf).addListener((ChannelFutureListener) future -> {
                    if (!future.isSuccess()) {
                        logger.error("send data to proxyServer exception occur: ", future.cause());
                    }
                });
            }else {
                logger.error("ProxyServerHandler channel is closed");
            }
        } else {
            logger.error("ProxyServerHandler No matching association");
        }
    }

    /**
     * 通道连接成功触发
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        logger.debug("代理服务连接成功："+ctx.channel().id());
        //新的连接建立后进行配对
        Channel internalChannel = ServerChannelGroup.forkChannel(ctx.channel());
        //发送启动代理客户端命令
        if(internalChannel.isActive()) {
            ByteBuf byteBuf = Unpooled.buffer();
            byteBuf.writeByte(FrameConstant.pv);
            long serial = System.currentTimeMillis();
            byteBuf.writeLong(serial);
            byteBuf.writeByte(CommandEnum.CMD_PROXY_START.getCmd());
            byteBuf.writeShort(FrameConstant.VC_CODE_LEN);
            //计算校验和
            int vc = 0;
            for (byte byteVal : BufUtil.getArray(byteBuf)) {
                vc = vc + (byteVal & 0xFF);
            }
            byteBuf.writeByte(vc);
            internalChannel.writeAndFlush(byteBuf).addListener((ChannelFutureListener) future -> {
                if (!future.isSuccess()) {
                    logger.error("proxyServer send data to sysClient exception occur: ", future.cause());
                }
            });
        }
    }

    /**
     * 通道断开时触发
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ServerChannelGroup.removeProxyChannel(ctx.channel());
        Channel internalChannel = ServerChannelGroup.getInternalByProxy(ctx.channel().id());
        //如果是proxy连接断开，将之前配对的internal连接移除配对，并在2秒后回归空闲连接池，相当于有2秒的time_wait状态
        ServerChannelGroup.removeChannelPair(internalChannel.id(), ctx.channel().id());
        Server.scheduledExecutor.schedule(
                () -> ServerChannelGroup.releaseInternalChannel(internalChannel),
                FrameConstant.CHANNEL_RELEASE_DELAY, TimeUnit.SECONDS);
    }

    /**
     * 通道异常触发
     * @param ctx
     * @param cause
     * @throws Exception
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        Channel channel = ctx.channel();
        if(!channel.isActive()){
            logger.debug("############### -- 代理服务 -- "+ channel.remoteAddress()+ "  断开了连接！");
            cause.printStackTrace();
            ctx.close();
        }else{
            ctx.fireExceptionCaught(cause);
            logger.debug("###############",cause);
        }
    }
}
