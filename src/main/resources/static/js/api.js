// Cliente HTTP da API do ProStock (sessão por cookie e token CSRF).

function obterCookie(nome) {
    const alvo = nome + "=";
    for (const parte of document.cookie.split(";")) {
        const c = parte.trim();
        if (c.startsWith(alvo)) return decodeURIComponent(c.substring(alvo.length));
    }
    return null;
}

const METODOS_MUTAVEIS = new Set(["POST", "PUT", "PATCH", "DELETE"]);

const api = {
    async request(metodo, caminho, corpo) {
        const opcoes = {
            method: metodo,
            credentials: "include",
            headers: {},
        };

        if (METODOS_MUTAVEIS.has(metodo)) {
            const token = obterCookie("XSRF-TOKEN");
            if (token) opcoes.headers["X-XSRF-TOKEN"] = token;
        }

        if (corpo !== undefined) {
            opcoes.headers["Content-Type"] = "application/json";
            opcoes.body = JSON.stringify(corpo);
        }

        const resposta = await fetch(caminho, opcoes);

        if (resposta.status === 204) {
            return null;
        }

        const contentType = resposta.headers.get("content-type") || "";
        const dados = contentType.includes("application/json") ? await resposta.json() : null;

        if (resposta.status === 401) {
            if (caminho === "/api/auth/login") {
                throw new Error((dados && dados.mensagem) || "E-mail ou senha inválidos.");
            }
            if (!caminho.includes("/api/auth/")) {
                window.location.href = "/login.html";
            }
            throw new Error("Sessão expirada. Faça login novamente.");
        }

        if (!resposta.ok) {
            const mensagem = dados && dados.mensagem ? dados.mensagem : "Erro inesperado ao comunicar com o servidor";
            throw new Error(mensagem);
        }

        return dados;
    },

    get(caminho) {
        return this.request("GET", caminho);
    },
    post(caminho, corpo) {
        return this.request("POST", caminho, corpo);
    },
    put(caminho, corpo) {
        return this.request("PUT", caminho, corpo);
    },
    patch(caminho, corpo) {
        return this.request("PATCH", caminho, corpo);
    },
    delete(caminho) {
        return this.request("DELETE", caminho);
    },
};
