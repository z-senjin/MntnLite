package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.client.plugins.microbot.mntn.builder.MntnBuilderOverlayState;
import net.runelite.client.plugins.microbot.mntn.builder.MntnBuilderRuntimeStatus;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only diagnostic view of the active Mntn Account Builder script. */
public class MntnBuilderStatusHandler extends AgentHandler {

    private static final String PATH = "/mntn-builder/status";

    public MntnBuilderStatusHandler(Gson gson) {
        super(gson);
    }

    @Override
    public String getPath() {
        return PATH;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        try {
            requireGet(exchange);
        } catch (HttpMethodException e) {
            sendJson(exchange, 405, errorResponse(e.getMessage()));
            return;
        }

        MntnBuilderOverlayState state = MntnBuilderRuntimeStatus.getLatest();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("available", state != null);
        if (state == null) {
            response.put("hint", "Start Mntn AIO Account Builder to publish its runtime status.");
            sendJson(exchange, 200, response);
            return;
        }

        response.put("runnerState", state.getRunnerState());
        response.put("goal", state.getGoal());
        response.put("requirement", state.getRequirement());
        response.put("activity", state.getActivity());
        response.put("strategy", state.getStrategy());
        response.put("task", state.getTask());
        response.put("taskStatus", state.getTaskStatus());
        response.put("lastStopReason", state.getLastStopReason());
        response.put("contentMode", state.getContentMode());
        response.put("sessionFlavor", state.getSessionFlavor());
        response.put("commitmentMillis", state.getCommitmentDuration() != null
                ? state.getCommitmentDuration().toMillis() : null);
        response.put("taskStartedAt", state.getTaskStartTime() != null
                ? state.getTaskStartTime().toString() : null);
        response.put("score", state.getScore());
        sendJson(exchange, 200, response);
    }
}
