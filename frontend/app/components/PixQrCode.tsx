'use client';

import { useEffect, useState } from 'react';
import QRCode from 'qrcode';

interface PixQrCodeProps {
  data: string;
  size?: number;
}

/**
 * Gera o QR Code do Pix inteiramente no cliente — o payload (dados
 * financeiros do pagamento) nunca é enviado a um serviço de terceiro.
 */
export function PixQrCode({ data, size = 180 }: PixQrCodeProps) {
  const [dataUrl, setDataUrl] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    QRCode.toDataURL(data, { width: size, margin: 1 })
      .then((url) => { if (!cancelled) setDataUrl(url); })
      .catch(() => { if (!cancelled) setDataUrl(null); });
    return () => { cancelled = true; };
  }, [data, size]);

  if (!dataUrl) {
    return <div style={{ width: size, height: size, background: '#eee', borderRadius: '8px' }} />;
  }

  return <img src={dataUrl} alt="Pix QR Code" width={size} height={size} style={{ borderRadius: '8px' }} />;
}
