import axios from "axios";

export const axiosClient = axios.create({
    baseURL: "https://localhost:8080/api",
    withCredentials: true,
});

export const login = async (username: string, password: string) => {
    const res = await axiosClient.post("/auth/login", { username, password });
    return res.data;
};

export const getCurrentUser = async () => {
    const res = await axiosClient.get("/auth/me");
    return res.data;
};
