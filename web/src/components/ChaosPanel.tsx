"use client";

import { useEffect, useRef, useState } from "react";
import type { Api } from "@/lib/api";
import { chaosPresets } from "@/lib/catalog";
import { percent } from "@/lib/format";
import type { Chaos } from "@/lib/types";

interface Props {
  api: Api;
  chaos: Chaos | null;
  onChange: (chaos: Chaos) => void;
}

const rates: { key: "failureRate" | "ackLossRate" | "duplicateRate"; label: string; help: string }[] = [
  { key: "failureRate", label: "Mensagem não chega", help: "Timeout antes do ERP receber." },
  { key: "ackLossRate", label: "Resposta se perde", help: "O ERP cria o pedido, mas a loja não fica sabendo." },
  { key: "duplicateRate", label: "Mensagem chega duas vezes", help: "A rede, ou o PSP do PIX, reenvia." },
];

export function ChaosPanel({ api, chaos, onChange }: Props) {
  const [draft, setDraft] = useState<Chaos | null>(chaos);
  const [error, setError] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    // acompanha o valor do servidor enquanto ninguém está mexendo nos controles
    if (!timer.current) setDraft(chaos);
  }, [chaos]);

  function change(next: Chaos) {
    setDraft(next);
    if (timer.current) clearTimeout(timer.current);
    timer.current = setTimeout(async () => {
      timer.current = null;
      try {
        onChange(await api.updateChaos(next));
        setError(null);
      } catch (e) {
        setError(e instanceof Error ? e.message : String(e));
      }
    }, 250);
  }

  if (!draft) {
    return (
      <section className="panel" aria-labelledby="chaos-title">
        <h2 id="chaos-title">Rede até o ERP</h2>
        <p className="muted">Carregando…</p>
      </section>
    );
  }

  return (
    <section className="panel" aria-labelledby="chaos-title">
      <h2 id="chaos-title">Rede até o ERP</h2>
      <div className="presets" role="group" aria-label="Combinações prontas">
        {Object.entries(chaosPresets).map(([id, preset]) => (
          <button key={id} type="button" className="chip" onClick={() => change({ ...preset.chaos })}>
            {preset.label}
          </button>
        ))}
      </div>

      {rates.map((rate) => (
        <label key={rate.key} className="slider">
          <span className="slider-head">
            <span>{rate.label}</span>
            <strong>{percent(draft[rate.key])}</strong>
          </span>
          <input
            type="range"
            min={0}
            max={100}
            step={5}
            value={Math.round(draft[rate.key] * 100)}
            onChange={(e) => change({ ...draft, [rate.key]: Number(e.target.value) / 100 })}
          />
          <small>{rate.help}</small>
        </label>
      ))}

      <label className="slider">
        <span className="slider-head">
          <span>Atraso por chamada</span>
          <strong>{draft.latencyMs} ms</strong>
        </span>
        <input
          type="range"
          min={0}
          max={1000}
          step={50}
          value={draft.latencyMs}
          onChange={(e) => change({ ...draft, latencyMs: Number(e.target.value) })}
        />
      </label>

      <p className="muted">A rede é a mesma para todo mundo que está vendo esta demo agora.</p>
      {error && (
        <p className="feedback" role="alert" data-tone="fail">
          {error}
        </p>
      )}
    </section>
  );
}
