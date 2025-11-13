import { useEffect, useState } from "react";
import { getAllUsers, deleteUser } from "../../api/auth";
import ConfirmPopup from "../../components/Popup/ConfirmPopup";
import "./UserPage.css";

interface AdminUser {
    id: number;
    username: string;
    roles: string[];
    createdAt: string;
}

interface PageResponse<T> {
    content: T[];
    page: {
        totalPages: number;
        totalElements: number;
        size: number;
        number: number;
    };
}

export default function UsersPage() {
    const [users, setUsers] = useState<AdminUser[]>([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(1);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [showPopup, setShowPopup] = useState(false);
    const [selectedUserId, setSelectedUserId] = useState<number | null>(null);

    const fetchUsers = async (pageNum = 0) => {
        setLoading(true);
        setError(null);
        try {
            const data: PageResponse<AdminUser> = await getAllUsers(pageNum, 10);
            console.log("📦 Backend pagination response:", data);

            const pageInfo = data.page || {}

            setUsers(data.content || []);
            setTotalPages(pageInfo.totalPages || 1);
            setPage(pageInfo.number || 0);
        } catch (err: any) {
            console.error("❌ Error fetching users:", err);
            setError("Failed to load users. Please try again.");
        } finally {
            setLoading(false);
        }
    };

    const handleDeleteConfirm = async () => {
        if (selectedUserId === null) return;
        try {
            await deleteUser(selectedUserId);
            const updated = users.filter((u) => u.id !== selectedUserId);
            setUsers(updated);

            if (updated.length === 0 && page > 0) {
                fetchUsers(page - 1);
            }
        } catch (err) {
            console.error("❌ Error deleting user:", err);
        } finally {
            setShowPopup(false);
            setSelectedUserId(null);
        }
    };

    const handleDeleteClick = (id: number) => {
        setSelectedUserId(id);
        setShowPopup(true);
    };

    useEffect(() => {
        fetchUsers(page);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    return (
        <div className="users-page">
            <h2 className="users-title">Manage Users</h2>

            {loading ? (
                <p className="loading">Loading users...</p>
            ) : error ? (
                <p className="error">{error}</p>
            ) : users.length === 0 ? (
                <p className="no-users">No users found.</p>
            ) : (
                <>
                    <table className="users-table">
                        <thead>
                        <tr>
                            <th>ID</th>
                            <th>Username</th>
                            <th>Roles</th>
                            <th>Created</th>
                            <th>Action</th>
                        </tr>
                        </thead>
                        <tbody>
                        {users.map((user) => (
                            <tr key={user.id}>
                                <td>{user.id}</td>
                                <td>{user.username}</td>
                                <td>{user.roles.join(", ")}</td>
                                <td>{new Date(user.createdAt).toLocaleString()}</td>
                                <td>
                                    <button
                                        className="delete-btn"
                                        onClick={() => handleDeleteClick(user.id)}
                                    >
                                        Delete
                                    </button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>

                    <div className="pagination">
                        <button
                            onClick={() => fetchUsers(page - 1)}
                            disabled={page === 0}
                        >
                            ← Prev
                        </button>
                        <span>
                            Page {Number(page) + 1} of {Number(totalPages) || 1}
                        </span>
                        <button
                            onClick={() => fetchUsers(page + 1)}
                            disabled={page + 1 >= totalPages}
                        >
                            Next →
                        </button>
                    </div>
                </>
            )}

            {showPopup && (
                <ConfirmPopup
                    message="Are you sure you want to delete this user?"
                    onConfirm={handleDeleteConfirm}
                    onCancel={() => setShowPopup(false)}
                />
            )}
        </div>
    );
}
