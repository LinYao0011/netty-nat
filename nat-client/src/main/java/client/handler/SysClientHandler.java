package client.handler;

import client.group.ClientChannelGroup;
import client.handler.Processor.ConnectionPoolProcessor;
import client.handler.Processor.HeartbeatProcessor;
import client.handler.Processor.LoginProcessor;
import core.constant.FrameConstant;
import core.enums.CommandEnum;
import core.enums.StringEnum;
import core.utils.BufUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author wneck130@gmail.com
 * @Function 代理程序的系统业务处理器
 */
public class SysClientHandler extends SimpleChannelInboundHandler<ByteBuf>{
    Logger log = LoggerFactory.getLogger(SysClientHandler.class);
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        //协议中第10个字节为命令字，getByte中index从0开始
        byte cmd = msg.getByte(FrameConstant.FRAME_CMD_INDEX);
        switch (cmd) {
            case (byte)0x01:
                new LoginProcessor().process(ctx, msg);
                break;
            case (byte)0x02:
                new HeartbeatProcessor().process(ctx, msg);
                break;
            case (byte)0x03:
                new ConnectionPoolProcessor().process(ctx, msg);
                break;
            case (byte)0x05:
                ClientChannelGroup.connectionPoolExpansion(msg);
                break;
            case (byte)0x06:
                ClientChannelGroup.removeInternalChannel(msg);
                break;
        }
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        //TCP连接建立后，马上发送登录信息给服务端
        byte[] password = StringEnum.LOGIN_PASSWORD.getValue().getBytes("UTF-8");
        int passwordLen = password.length;
        //密码长度字节的长度为1
        int passwordLenLen = 1;
        ByteBuf byteBuf = Unpooled.buffer();
        byteBuf.writeByte(FrameConstant.pv);
        long serial = System.currentTimeMillis();
        byteBuf.writeLong(serial);
        byteBuf.writeByte(CommandEnum.CMD_LOGIN.getCmd());
        byteBuf.writeShort(passwordLenLen + passwordLen + FrameConstant.VC_CODE_LEN);
        byteBuf.writeByte(passwordLen);
        byteBuf.writeBytes(password);
        //计算校验和
        int vc = 0;
        for (byte byteVal : BufUtil.getArray(byteBuf)) {
            vc = vc + (byteVal & 0xFF);
        }
        byteBuf.writeByte(vc);
        ctx.writeAndFlush(byteBuf);

        //发送心跳命令
        ByteBuf buf = Unpooled.buffer();
        buf.writeByte(FrameConstant.pv);
        buf.writeLong(System.currentTimeMillis());
        buf.writeByte(CommandEnum.CMD_HEARTBEAT.getCmd());
        buf.writeShort(FrameConstant.VC_CODE_LEN);
        //计算校验和
        int vc1 = 0;
        for (byte byteVal : BufUtil.getArray(buf)) {
            vc1 = vc1 + (byteVal & 0xFF);
        }
        buf.writeByte(vc1);
        ctx.writeAndFlush(buf);
    }



    //服务端断开触发
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
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
            log.debug("############### -- 客户端 -- "+ channel.remoteAddress()+ "  断开了连接！");
            cause.printStackTrace();
            ctx.close();
        }else{
            ctx.fireExceptionCaught(cause);
            log.debug("###############",cause);
        }
    }
}
