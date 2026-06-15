import { useState } from 'react';
import api from '@/app/services/api';
import { toast } from 'sonner';

export const useAuditCRUD = (endpoint: string) => {
    const [loading, setLoading] = useState(false);

    const list = async (params = {}, subPath = "") => {
        setLoading(true);
        try {
            const res = await api.get(`${endpoint}${subPath}`, { params });
            return res.data;
        } catch (error: any) {
            toast.error(`Erro ao carregar dados: ${error.message}`);
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
            toast.error(`Erro ao criar registro: ${error.message}`);
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
            toast.error(`Erro ao atualizar registro: ${error.message}`);
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
            toast.error(`Erro ao remover registro: ${error.message}`);
            throw error;
        } finally {
            setLoading(false);
        }
    };

    return { list, create, update, remove, loading };
};
