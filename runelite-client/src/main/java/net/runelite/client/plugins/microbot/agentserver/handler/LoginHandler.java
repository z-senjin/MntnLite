package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.security.HopBudget;
import net.runelite.client.plugins.microbot.util.security.LoginLockout;
import net.runelite.client.plugins.microbot.util.security.LoginManager;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class LoginHandler extends AgentHandler {

	private static final int LOGIN_INDEX_AUTH_FAILURE = 3;
	private static final int LOGIN_INDEX_INVALID_CREDENTIALS = 4;
	private static final int LOGIN_INDEX_BANNED = 14;
	private static final int LOGIN_INDEX_DISCONNECTED = 24;
	private static final int LOGIN_INDEX_NON_MEMBER = 34;
	private static final int LOGIN_INDEX_TOO_MANY_ATTEMPTS = LoginLockout.LOGIN_INDEX_TOO_MANY_ATTEMPTS;

	private static final Set<Integer> FATAL_LOGIN_INDICES = Set.of(
			LOGIN_INDEX_AUTH_FAILURE,
			LOGIN_INDEX_INVALID_CREDENTIALS,
			LOGIN_INDEX_BANNED,
			LOGIN_INDEX_NON_MEMBER,
			LOGIN_INDEX_TOO_MANY_ATTEMPTS
	);

	private static final int DEFAULT_TIMEOUT_SECONDS = 30;
	private static final int MAX_TIMEOUT_SECONDS = 120;
	private static final int POLL_INTERVAL_MS = 600;
	private static final int LOGIN_STABILIZATION_MS = 3000;
	private static final int STABILIZATION_POLL_MS = 300;

	private final Client client;

	public LoginHandler(Gson gson, Client client) {
		super(gson);
		this.client = client;
	}

	@Override
	public String getPath() {
		return "/login";
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		String method = exchange.getRequestMethod().toUpperCase();

		if ("GET".equals(method)) {
			handleStatus(exchange);
		} else if ("POST".equals(method)) {
			handleLogin(exchange);
		} else {
			sendJson(exchange, 405, errorResponse("Use GET or POST"));
		}
	}

	private void handleStatus(HttpExchange exchange) throws IOException {
		sendJson(exchange, 200, buildStatusMap());
	}

	private Map<String, Object> buildStatusMap() {
		Map<String, Object> result = new LinkedHashMap<>();

		GameState gameState = client.getGameState();
		result.put("loggedIn", Microbot.isLoggedIn());
		result.put("gameState", gameState.name());
		result.put("loginAttemptActive", LoginManager.isLoginAttemptActive());
		long loginCooldownMs = LoginLockout.getInstance().remainingMillis();
		if (loginCooldownMs > 0) {
			result.put("loginCooldownMs", loginCooldownMs);
		}
		HopBudget hopBudget = HopBudget.getInstance();
		if (hopBudget.isEnabled()) {
			result.put("hopsAvailable", hopBudget.available());
			result.put("nextHopInMs", hopBudget.millisUntilHopAllowed());
		}

		if (Microbot.isLoggedIn()) {
			long durationMs = LoginManager.getLoginDuration().toMillis();
			result.put("loginDurationMs", durationMs);
		}

		if (gameState == GameState.LOGIN_SCREEN) {
			int loginIndex = client.getLoginIndex();
			result.put("loginIndex", loginIndex);
			String loginError = describeLoginIndex(loginIndex);
			if (loginError != null) {
				result.put("loginError", loginError);
			}
		}

		try {
			ConfigProfile profile = LoginManager.getActiveProfile();
			if (profile != null) {
				Map<String, Object> profileInfo = new LinkedHashMap<>();
				profileInfo.put("name", profile.getName());
				profileInfo.put("isMember", profile.isMember());
				profileInfo.put("selectedWorld", profile.getSelectedWorld());
				result.put("activeProfile", profileInfo);
			}
		} catch (Exception e) {
			log.debug("Failed to read active profile", e);
		}

		result.put("currentWorld", client.getWorld());

		return result;
	}

	private void handleLogin(HttpExchange exchange) throws IOException {
		if (Microbot.isLoggedIn()) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("message", "Already logged in");
			result.put("currentWorld", client.getWorld());
			sendJson(exchange, 200, result);
			return;
		}

		if (LoginManager.isLoginAttemptActive()) {
			sendJson(exchange, 409, errorResponse("Login attempt already in progress"));
			return;
		}

		Map<String, Object> body = readJsonBody(exchange);

		int targetWorld = -1;
		boolean wait = true;
		int timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;

		if (body != null) {
			if (body.containsKey("world")) {
				targetWorld = ((Number) body.get("world")).intValue();
			}
			if (body.containsKey("wait")) {
				wait = Boolean.TRUE.equals(body.get("wait"));
			}
			if (body.containsKey("timeout")) {
				timeoutSeconds = Math.min(((Number) body.get("timeout")).intValue(), MAX_TIMEOUT_SECONDS);
				if (timeoutSeconds <= 0) timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
			}
		}

		boolean loginInitiated;
		try {
			if (targetWorld > 0) {
				loginInitiated = LoginManager.login(targetWorld);
			} else {
				loginInitiated = LoginManager.login();
			}
		} catch (Exception e) {
			log.error("Login attempt failed", e);
			sendJson(exchange, 500, errorResponse("Login failed: " + e.getMessage()));
			return;
		}

		if (!loginInitiated && LoginLockout.getInstance().isActive()) {
			sendLoginCooldown(exchange);
			return;
		}

		if (!loginInitiated) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", false);
			result.put("message", "Login rejected - check that an active profile is configured and client is on the login screen");
			sendJson(exchange, 400, result);
			return;
		}

		if (!wait) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("message", "Login initiated (not waiting for result)");
			if (targetWorld > 0) {
				result.put("world", targetWorld);
			}
			sendJson(exchange, 200, result);
			return;
		}

		LoginResult loginResult = waitForLoginResult(timeoutSeconds);

		if (!loginResult.success && LoginLockout.isLockoutLoginIndex(loginResult.loginIndex)) {
			LoginManager.checkLoginLockout();
			sendLoginCooldown(exchange);
			return;
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", loginResult.success);
		result.put("message", loginResult.message);
		result.put("currentWorld", client.getWorld());

		if (loginResult.loginIndex > 0) {
			result.put("loginIndex", loginResult.loginIndex);
			String errorDesc = describeLoginIndex(loginResult.loginIndex);
			if (errorDesc != null) {
				result.put("loginError", errorDesc);
			}
		}

		sendJson(exchange, loginResult.success ? 200 : 401, result);
	}

	private void sendLoginCooldown(HttpExchange exchange) throws IOException {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", false);
		result.put("message", "Too many login attempts - login paused until the cooldown ends");
		result.put("loginIndex", LOGIN_INDEX_TOO_MANY_ATTEMPTS);
		result.put("loginCooldownMs", LoginLockout.getInstance().remainingMillis());
		sendJson(exchange, 429, result);
	}

	private LoginResult waitForLoginResult(int timeoutSeconds) {
		long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);

		while (System.currentTimeMillis() < deadline) {
			if (Microbot.isLoggedIn()) {
				LoginResult stable = waitForStableLogin();
				if (stable != null) {
					return stable;
				}
				continue;
			}

			GameState gs = client.getGameState();
			if (gs == GameState.LOGIN_SCREEN) {
				int idx = client.getLoginIndex();
				if (FATAL_LOGIN_INDICES.contains(idx)) {
					String desc = describeLoginIndex(idx);
					return LoginResult.fail("Login failed: " + desc, idx);
				}
			}

			try {
				Thread.sleep(POLL_INTERVAL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return LoginResult.fail("Login interrupted", 0);
			}
		}

		return LoginResult.fail("Login timed out after " + timeoutSeconds + "s", 0);
	}

	private LoginResult waitForStableLogin() {
		long stabilizeDeadline = System.currentTimeMillis() + LOGIN_STABILIZATION_MS;

		while (System.currentTimeMillis() < stabilizeDeadline) {
			if (!Microbot.isLoggedIn()) {
				GameState gs = client.getGameState();
				if (gs == GameState.LOGIN_SCREEN) {
					int idx = client.getLoginIndex();
					if (FATAL_LOGIN_INDICES.contains(idx)) {
						String desc = describeLoginIndex(idx);
						return LoginResult.fail("Login failed: " + desc, idx);
					}
				}
				return null;
			}

			try {
				Thread.sleep(STABILIZATION_POLL_MS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return LoginResult.fail("Login interrupted", 0);
			}
		}

		return LoginResult.ok("Login successful");
	}

	private static String describeLoginIndex(int loginIndex) {
		switch (loginIndex) {
			case LOGIN_INDEX_AUTH_FAILURE:
				return "Authentication failed - invalid credentials";
			case LOGIN_INDEX_INVALID_CREDENTIALS:
				return "Invalid credentials";
			case LOGIN_INDEX_BANNED:
				return "Account is banned";
			case LOGIN_INDEX_DISCONNECTED:
				return "Disconnected from server";
			case LOGIN_INDEX_NON_MEMBER:
				return "Non-member account cannot login to members world";
			case LOGIN_INDEX_TOO_MANY_ATTEMPTS:
				return "Too many login attempts - wait before retrying";
			default:
				return null;
		}
	}

	private static class LoginResult {
		final boolean success;
		final String message;
		final int loginIndex;

		LoginResult(boolean success, String message, int loginIndex) {
			this.success = success;
			this.message = message;
			this.loginIndex = loginIndex;
		}

		static LoginResult ok(String message) {
			return new LoginResult(true, message, 0);
		}

		static LoginResult fail(String message, int loginIndex) {
			return new LoginResult(false, message, loginIndex);
		}
	}
}
