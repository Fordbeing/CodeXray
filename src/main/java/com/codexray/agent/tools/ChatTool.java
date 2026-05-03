package com.codexray.agent.tools;

import java.util.Map;

/**
 * 聊天 Agent 可调用的工具接口。
 */
public interface ChatTool {
    String name();
    String description();
    String paramSchema();
    String execute(Map<String, String> args);
}
