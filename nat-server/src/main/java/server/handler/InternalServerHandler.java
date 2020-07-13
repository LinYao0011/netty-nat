package server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.SimpleChannelInboundHandler;
import server.group.ServerChannelGroup;

public class InternalServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        //通过内部的internalChannel收到响应详细，转发到代理服务的请求者
        ChannelId channelId = ctx.channel().id();
        if (ServerChannelGroup.channelPairExist(channelId)) {
            //已经存在配对的连接，直接发送，响应时无配对数据可能是channel断开连接导致的，结束消息传递
            ServerChannelGroup.getProxyByInternal(channelId).writeAndFlush(msg);
        }
    }

    /**
     * 内部通道建立缓存机制，所有可用通道进入空闲连接池，等待配对
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        ServerChannelGroup.addIdleInternalChannel(ctx.channel());
    }

    /**
     * 通道断开连接时回收已经配对的信息
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        ServerChannelGroup.removeIdleInternalChannel(ctx.channel());
        ServerChannelGroup.removeInternalChannel(ctx.channel());
        Channel proxyChannel = ServerChannelGroup.getProxyByInternal(ctx.channel().id());
        ServerChannelGroup.removeChannelPair(ctx.channel().id(), proxyChannel.id());
        proxyChannel.close();
    }
}
