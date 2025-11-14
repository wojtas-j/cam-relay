import axios, { AxiosError, type AxiosInstance } from "axios";
import { logout as backendLogout } from "./auth";

export const axiosClient: AxiosInstance = axios.create({
    baseURL: "https://localhost:8080/api",
    withCredentials: true,
});

export const login = async (username: string, password: string) => {
    const res = await axiosClient.post("/auth/login", { username, password });
    return res.data;
};

export const logout = async () => {
    await axiosClient.post("/auth/logout");
};

export const getCurrentUser = async () => {
    const res = await axiosClient.get("/auth/me");
    return res.data;
};

export const refreshToken = async () => {
    return axiosClient.post("/auth/refresh");
};

export const deleteAccount = async () => {
    return axiosClient.delete("/users");
};

export const getAllUsers = async (page = 0, size = 10) => {
    const res = await axiosClient.get(`/admin/users?page=${page}&size=${size}`);
    return res.data;
};

export const deleteUser = async (id: number) => {
    const res = await axiosClient.delete(`/admin/users/${id}`);
    return res.data;
};

export const getWebSocketUrl = (path = "/ws") => {
    const host = window.location.hostname;
    const port = 8080;
    return `wss://${host}:${port}${path}`;
};

export const createUser = async (username: string, password: string, roles: string[]) => {
    try {
        const res = await axiosClient.post("/admin/create", { username, password, roles });
        return res.data;
    } catch (error) {
        const axiosErr = error as AxiosError<any>;
        if (!axiosErr.response) {
            throw {
                status: 0,
                message: "Server unreachable. Please check your connection.",
                data: null,
            };
        }

        const status = axiosErr.response?.status;
        const data = axiosErr.response?.data;

        console.log("🚨 Caught createUser error in auth.ts:", { status, data });

        const message =
            data?.detail ||
            data?.message ||
            data?.error ||
            Object.values(data ?? {}).flat().join("; ") ||
            "Unexpected error while creating user.";

        throw {
            status,
            message,
            data,
        };
    }
};

let isRefreshing = false;
let refreshSubscribers: ((tokenRefreshed: boolean) => void)[] = [];

const onRefreshed = (tokenRefreshed: boolean) => {
    refreshSubscribers.forEach((cb) => cb(tokenRefreshed));
    refreshSubscribers = [];
};

axiosClient.interceptors.response.use(
    (response) => response,
    async (error: AxiosError) => {
        const originalRequest: any = error.config;

        if (error.response?.status !== 401) {
            return Promise.reject(error);
        }

        if (originalRequest.url?.includes("/auth/refresh") || originalRequest.url?.includes("/auth/login")) {
            return Promise.reject(error);
        }

        if (isRefreshing) {
            return new Promise((resolve, reject) => {
                refreshSubscribers.push((success) => {
                    if (success) resolve(axiosClient(originalRequest));
                    else reject(error);
                });
            });
        }

        isRefreshing = true;

        try {
            await refreshToken();
            console.log("%c✅ Token refreshed successfully", "color: green; font-weight: bold");

            onRefreshed(true);
            return axiosClient(originalRequest);
        } catch (refreshError) {
            console.error("%c❌ Token refresh failed", "color: red; font-weight: bold");
            onRefreshed(false);

            try {
                await backendLogout();
            } catch {}

            window.dispatchEvent(new Event("force-logout"));
            window.location.href = "/login";

            return Promise.reject(refreshError);
        } finally {
            isRefreshing = false;
        }
    }
);
