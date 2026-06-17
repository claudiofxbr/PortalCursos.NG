import { useState } from 'react';
import api from '@/app/services/api';
import { toast } from 'sonner';

const extractUserMessage = (error: any): string => {
    const serverMsg = error?.response?.data?.message;
    if (serverMsg && typeof serverMsg === 'string') return serverMsg;
    const status = error?.response?.status;
    if (status === 403) return 'Acesso negado: você não tem permissão para esta ação.';
    if (status === 404) return 'Registro não encontrado.';
    if (status === 409) return 'Conflito: este registro já existe ou há dados duplicados.';
    if (status >= 500) return 'Erro interno do servidor. Tente novamente em instantes.';
    return 'Erro ao comunicar com o servidor. Verifique sua conexão.';
};

export const useAuditCRUD = (endpoint: string) => {
    const [loading, setLoading] = useState(false);

    const list = async (params = {}, subPath = "") => {
        setLoading(true);
        try {
            const res = await api.get(`${endpoint}${subPath}`, { params });
            return res.data;
        } catch (error: any) {
            toast.error(extractUserMessage(error));
            throw error;
        } finally {
            setLoading(false);
        }
    };

    const create = async (data: any, subPath = "") => {
        setLoading(true);
        try {
            const res = await api.post(`${endpoint}${subPath}`, data);
            toast.success('Registro criado com sucesso!');
            return res.data;
        } catch (error: any) {
            toast.error(extractUserMessage(error));
            throw error;
        } finally {
            setLoading(false);
        }
    };

    const update = async (id: number | string, data: any, subPath = "") => {
        setLoading(true);
        try {
            const res = await api.put(`${endpoint}${subPath}/${id}`, data);
            toast.success('Registro atualizado com sucesso!');
            return res.data;
        } catch (error: any) {
            toast.error(extractUserMessage(error));
            throw error;
        } finally {
            setLoading(false);
        }
    };

    const remove = async (id: number | string, subPath = "") => {
        setLoading(true);
        try {
            await api.delete(`${endpoint}${subPath}/${id}`);
            toast.success('Registro removido com sucesso!');
            return true;
        } catch (error: any) {
            toast.error(extractUserMessage(error));
            throw error;
        } finally {
            setLoading(false);
        }
    };

    return { list, create, update, remove, loading };
};
