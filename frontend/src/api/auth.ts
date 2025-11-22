import axios, { AxiosError} from "axios";

export const axiosClient = axios.create({
    baseURL: `https://${import.meta.env.VITE_API_HOST}/api`,
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
    const host = import.meta.env.VITE_API_HOST;

    return `wss://${host}${path}`;
};

export const getReceivers = async () => {
    const res = await axiosClient.get("/users/receivers");
    return res.data;
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

export const ICE_CONFIG: RTCConfiguration = {
    iceServers: [
        {
            urls: `turn:${import.meta.env.VITE_API_HOST}:${import.meta.env.VITE_TURN_PORT}?transport=udp`,
            username: import.meta.env.VITE_TURN_USERNAME,
            credential: import.meta.env.VITE_TURN_PASSWORD
        },
        {
            urls: `turn:${import.meta.env.VITE_API_HOST}:${import.meta.env.VITE_TURN_PORT}?transport=tcp`,
            username: import.meta.env.VITE_TURN_USERNAME,
            credential: import.meta.env.VITE_TURN_PASSWORD
        },
        { urls: import.meta.env.VITE_STUN_URL }
    ]
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

        const url = originalRequest.url ?? "";
        if (url.includes("/auth/refresh") || url.includes("/auth/login")) {
            window.dispatchEvent(new Event("force-logout"));
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
            console.log("✅ Token refreshed");

            onRefreshed(true);
            return axiosClient(originalRequest);
        } catch (refreshError) {
            console.log("❌ Refresh failed");

            onRefreshed(false);

            window.dispatchEvent(new Event("force-logout"));

            return Promise.reject(refreshError);
        } finally {
            isRefreshing = false;
        }
    }
);

