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
            console.log("%c🔄 Refreshing access token...", "color: orange; font-weight: bold");
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
