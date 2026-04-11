'use client';

import React, { useState, useRef } from 'react';
import { Camera, Upload, X, Check } from 'lucide-react';

interface PhotoUpload3x4Props {
  onPhotoSelected: (file: File | null) => void;
  label?: string;
}

export default function PhotoUpload3x4({ onPhotoSelected, label = "Foto 3x4" }: PhotoUpload3x4Props) {
  const [preview, setPreview] = useState<string | null>(null);
  const [isHovered, setIsHovered] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (file.size > 2 * 1024 * 1024) {
        alert("A foto deve ter no máximo 2MB");
        return;
      }
      
      const reader = new FileReader();
      reader.onloadend = () => {
        setPreview(reader.result as string);
      };
      reader.readAsDataURL(file);
      onPhotoSelected(file);
    }
  };

  const removePhoto = (e: React.MouseEvent) => {
    e.stopPropagation();
    setPreview(null);
    onPhotoSelected(null);
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  return (
    <div className="photo-upload-container">
      <label className="photo-upload-label">{label}</label>
      
      <div 
        className={`photo-frame ${preview ? 'has-photo' : ''} ${isHovered ? 'hover' : ''}`}
        onClick={() => fileInputRef.current?.click()}
        onMouseEnter={() => setIsHovered(true)}
        onMouseLeave={() => setIsHovered(false)}
      >
        <input 
          type="file" 
          ref={fileInputRef}
          onChange={handleFileChange}
          accept="image/*"
          className="hidden-input"
        />

        {preview ? (
          <div className="photo-preview-wrapper">
            <img src={preview} alt="Preview 3x4" className="photo-image" />
            {isHovered && (
              <div className="photo-overlay">
                <button type="button" className="remove-btn" onClick={removePhoto}>
                  <X size={20} />
                </button>
                <div className="change-hint">
                  <Upload size={16} /> Alterar
                </div>
              </div>
            )}
            <div className="success-badge">
              <Check size={12} />
            </div>
          </div>
        ) : (
          <div className="photo-placeholder">
            <Camera size={32} className="placeholder-icon" />
            <span>Selecione uma Foto 3x4</span>
            <p>Clique aqui para enviar</p>
          </div>
        )}
      </div>

      <style jsx>{`
        .photo-upload-container {
          display: flex;
          flex-direction: column;
          align-items: center;
          gap: 0.75rem;
          margin-bottom: 1.5rem;
        }

        .photo-upload-label {
          font-weight: 600;
          color: var(--foreground);
          font-size: 0.9rem;
          text-transform: uppercase;
          letter-spacing: 0.05em;
        }

        .photo-frame {
          width: 150px; /* Base 3 */
          height: 200px; /* Altura 4 */
          border: 2px dashed rgba(var(--primary-rgb), 0.3);
          border-radius: 12px;
          cursor: pointer;
          position: relative;
          background: rgba(var(--primary-rgb), 0.02);
          transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
          overflow: hidden;
          display: flex;
          justify-content: center;
          align-items: center;
        }

        .photo-frame:hover {
          border-color: var(--primary);
          background: rgba(var(--primary-rgb), 0.05);
          transform: translateY(-2px);
          box-shadow: 0 8px 24px rgba(0,0,0,0.1);
        }

        .photo-frame.has-photo {
          border-style: solid;
          border-color: var(--primary);
        }

        .hidden-input {
          display: none;
        }

        .photo-placeholder {
          display: flex;
          flex-direction: column;
          align-items: center;
          text-align: center;
          padding: 1rem;
          color: var(--muted-foreground);
        }

        .placeholder-icon {
          margin-bottom: 0.75rem;
          opacity: 0.6;
        }

        .photo-placeholder span {
          font-size: 0.85rem;
          font-weight: 500;
        }

        .photo-placeholder p {
          font-size: 0.7rem;
          margin-top: 0.25rem;
          opacity: 0.7;
        }

        .photo-preview-wrapper {
          width: 100%;
          height: 100%;
          position: relative;
        }

        .photo-image {
          width: 100%;
          height: 100%;
          object-fit: cover;
        }

        .photo-overlay {
          position: absolute;
          inset: 0;
          background: rgba(0,0,0,0.4);
          backdrop-filter: blur(2px);
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          color: white;
          animation: fadeIn 0.2s ease;
        }

        .remove-btn {
          position: absolute;
          top: 8px;
          right: 8px;
          background: rgba(255, 0, 0, 0.7);
          border: none;
          color: white;
          width: 30px;
          height: 30px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          cursor: pointer;
          transition: background 0.2s;
        }

        .remove-btn:hover {
          background: rgba(255, 0, 0, 0.9);
        }

        .change-hint {
          display: flex;
          align-items: center;
          gap: 0.5rem;
          font-size: 0.85rem;
          font-weight: 600;
        }

        .success-badge {
          position: absolute;
          bottom: 10px;
          right: 10px;
          background: #10b981;
          color: white;
          width: 20px;
          height: 20px;
          border-radius: 50%;
          display: flex;
          align-items: center;
          justify-content: center;
          box-shadow: 0 2px 8px rgba(0,0,0,0.2);
        }

        @keyframes fadeIn {
          from { opacity: 0; }
          to { opacity: 1; }
        }
      `}</style>
    </div>
  );
}
