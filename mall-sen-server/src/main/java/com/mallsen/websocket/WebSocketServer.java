package com.mallsen.websocket;

import org.springframework.stereotype.Component;
import jakarta.websocket.OnClose;        // 改这里
import jakarta.websocket.OnMessage;      // 改这里
import jakarta.websocket.OnOpen;         // 改这里
import jakarta.websocket.Session;        // 改这里
import jakarta.websocket.server.PathParam;    // 改这里
import jakarta.websocket.server.ServerEndpoint;  // 改这里
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket服务
 */
@Component
@ServerEndpoint("/ws/{sid}")
@Slf4j
public class WebSocketServer {
    //启动前需要在cpolar文件中打开cmd 然后输入 cp按下Tab空格http空格8080来获取临时域名，在把临时域名赋值粘贴到yml中

    //存放会话对象
    private static Map<String, Session> sessionMap = new HashMap();

    /**
     * 连接建立成功调用的方法
     */
    @OnOpen
    public void onOpen(Session session, @PathParam("sid") String sid) {
        log.info("客户端：{}建立连接", sid);
        sessionMap.put(sid, session);
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, @PathParam("sid") String sid) {
        log.info("收到来自客户端：{}的信息:{}", sid, message);
    }

    /**
     * 连接关闭调用的方法
     *
     * @param sid
     */
    @OnClose
    public void onClose(@PathParam("sid") String sid) {
        log.info("连接断开:{}", sid);
        sessionMap.remove(sid);
    }

    /**
     * 群发
     *
     * @param message
     */
    public void sendToAllClient(String message) {
        Collection<Session> sessions = sessionMap.values();
        for (Session session : sessions) {
            try {
                //服务器向客户端发送消息
                session.getBasicRemote().sendText(message);
            } catch (Exception e) {
                log.error("WebSocket发送消息失败, session: {}", session.getId(), e);
            }
        }
    }

}
