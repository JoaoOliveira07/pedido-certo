import { createApi } from "./api";

export const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
export const REPO_URL = "https://github.com/JoaoOliveira07/pedido-certo";

export const api = createApi(API_URL);
