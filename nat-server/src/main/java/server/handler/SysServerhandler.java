package server.handler;

import core.constant.FrameConstant;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import server.handler.processor.ConnectionPoolProcessor;
import server.handler.processor.HeartbeatProcessor;
import server.handler.processor.LoginProcessor;

public class SysServerhandler extends SimpleChannelInboundHandler<ByteBuf> {
    private static final Logger logger = LoggerFactory.getLogger(SysServerhandler.class);
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        byte cmd = msg.getByte(FrameConstant.FRAME_CMD_INDEX);
        switch (cmd) {
            //接入命令
            case 0x01:
                new LoginProcessor().process(ctx, msg);
                //15秒心跳检测
                new HeartbeatProcessor().timeoutDetection(ctx);
                break;
            //心跳命令
            case 0x02:
                new HeartbeatProcessor().process(ctx, msg);
                break;
            //建立内部连接池命令
            case 0x03:
                new ConnectionPoolProcessor().process(ctx, msg);
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        super.channelInactive(ctx);
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
            logger.debug("############### -- 客户端 -- "+ channel.remoteAddress()+ "  断开了连接！");
            cause.printStackTrace();
            ctx.close();
        }else{
            ctx.fireExceptionCaught(cause);
            logger.debug("###############",cause);
        }
    }
}
