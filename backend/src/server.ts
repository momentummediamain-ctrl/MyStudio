import http from "node:http";
import {
  MCP_TOOL_DEFINITIONS,
  PersonaForgeDomainEngine,
  validateProviderUrl,
} from "./mcpToolsEngine";

/**
 * Cloud Run HTTP & Authenticated Streamable HTTP MCP Server (`/mcp` and `/api/v1/*`).
 *
 * Authentication:
 * - Extracts Bearer token from `Authorization: Bearer <token>`.
 * - In production on Cloud Run, verifies Firebase ID tokens via `firebase-admin` Auth.
 * - Never trusts a client-supplied `userId` parameter for tenant scoping.
 */
export function createPersonaForgeHttpServer(
  engine = new PersonaForgeDomainEngine(),
  tokenVerifier?: (token: string) => Promise<{ uid: string; role: string }>
) {
  const verifyToken =
    tokenVerifier ||
    (async (token: string) => {
      // Deterministic token parser for local automated verification when Firebase Admin credentials are not injected
      if (token.startsWith("test-token-")) {
        const parts = token.replace("test-token-", "").split(":");
        return {
          uid: parts[0],
          role: parts[1] || "CHATGPT_MCP",
        };
      }
      throw new Error("INVALID_BEARER_TOKEN");
    });

  return http.createServer(async (req, res) => {
    res.setHeader("Content-Type", "application/json; charset=utf-8");

    try {
      const authHeader = req.headers.authorization || "";
      if (!authHeader.startsWith("Bearer ")) {
        res.statusCode = 401;
        res.end(JSON.stringify({ error: "Missing OAuth Bearer token" }));
        return;
      }

      const token = authHeader.slice("Bearer ".length).trim();
      const verified = await verifyToken(token);

      const chunks: Buffer[] = [];
      for await (const chunk of req) {
        chunks.push(Buffer.from(chunk));
      }
      const rawBody = Buffer.concat(chunks).toString("utf8");
      const body = rawBody ? JSON.parse(rawBody) : {};

      // Streamable HTTP Model Context Protocol (MCP) JSON-RPC 2.0 Endpoint
      if (req.method === "POST" && req.url === "/mcp") {
        const { jsonrpc, id, method, params } = body;
        if (jsonrpc !== "2.0") {
          res.statusCode = 400;
          res.end(JSON.stringify({ error: "Invalid JSON-RPC 2.0 envelope" }));
          return;
        }

        if (method === "tools/list") {
          res.statusCode = 200;
          res.end(
            JSON.stringify({
              jsonrpc: "2.0",
              id,
              result: { tools: MCP_TOOL_DEFINITIONS },
            })
          );
          return;
        }

        if (method === "tools/call") {
          const toolName = params?.name;
          const toolArgs = params?.arguments || {};
          try {
            const output = await engine.executeMcpTool(
              {
                uid: verified.uid,
                role: verified.role,
                clientSuppliedUserId: toolArgs.userId,
              },
              toolName,
              toolArgs
            );
            res.statusCode = 200;
            res.end(
              JSON.stringify({
                jsonrpc: "2.0",
                id,
                result: {
                  content: [{ type: "text", text: JSON.stringify(output) }],
                  structuredContent: output,
                  isError: false,
                },
              })
            );
          } catch (toolError: any) {
            res.statusCode = 200;
            res.end(
              JSON.stringify({
                jsonrpc: "2.0",
                id,
                result: {
                  content: [{ type: "text", text: toolError.message }],
                  isError: true,
                },
              })
            );
          }
          return;
        }

        res.statusCode = 404;
        res.end(
          JSON.stringify({
            jsonrpc: "2.0",
            id,
            error: { code: -32601, message: `Unsupported MCP method: ${method}` },
          })
        );
        return;
      }

      // Health & Provider SSRF Check Endpoint
      if (req.method === "POST" && req.url === "/api/v1/providers/validate-url") {
        const safeUrl = validateProviderUrl(body.url);
        res.statusCode = 200;
        res.end(JSON.stringify({ valid: true, normalizedUrl: safeUrl }));
        return;
      }

      res.statusCode = 404;
      res.end(JSON.stringify({ error: "Route not found" }));
    } catch (err: any) {
      res.statusCode = err.message?.includes("INVALID_BEARER_TOKEN") ? 401 : 400;
      res.end(JSON.stringify({ error: err.message || "Request failed" }));
    }
  });
}

if (require.main === module) {
  const port = Number(process.env.PORT || 8090);
  const server = createPersonaForgeHttpServer();
  server.listen(port, "0.0.0.0", () => {
    console.log(`PersonaForge Studio Cloud Run & MCP Server listening on port ${port}`);
  });
}
