import { useState } from 'react';
import api from '@/app/services/api';
import { useAuth } from '@/app/context/AuthContext';
import { toast } from 'sonner';

export const useAuditCRUD = (endpoint: string) => {
    const { user } = useAuth();
    const [loading, setLoading] = useState(false);

    const getAuditData = () => ({
        creatorName: user?.username || 'Sistema',
        creatorPosition: user?.roles?.[0] || 'Operador',
        creatorPhotoUrl: user?.fotoUrl || null
    });

    const list = async (params = {}) => {
        setLoading(true);
        try {
            const res = await api.get(endpoint, { params });
            return res.data;
        } catch (error: any) {
            toast.error(`Erro ao carregar dados: ${error.message}`);
            throw error;
        } finally {
            setLoading(false);
        }
    };

    const create = async (data: any) => {
        setLoading(true);
        try {
            const auditPayload = { ...data, ...getAuditData() };
            const res = await api.post(endpoint, auditPayload);
            toast.success('Registro criado com sucesso!');
            return res.data;
        } catch (error: any) {
            toast.error(`Erro ao criar registro: ${error.message}`);
            throw error;
        } finally {
            setLoading(false);
        }
    };

    const update = async (id: number | string, data: any) => {
        setLoading(true);
        try {
            const auditPayload = { ...data, ...getAuditData() };
            const res = await api.put(`${endpoint}/${id}`, auditPayload);
            toast.success('Registro atualizado com sucesso!');
            return res.data;
        } catch (error: any) {
            toast.error(`Erro ao atualizar registro: ${error.message}`);
            throw error;
        } finally {
            setLoading(false);
        }
    };

    const remove = async (id: number | string) => {
        setLoading(true);
        try {
            await api.delete(`${endpoint}/${id}`);
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
