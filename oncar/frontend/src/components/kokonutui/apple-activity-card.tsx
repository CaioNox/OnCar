"use client";

/**
 * @author: @kokonutui
 * @description: Apple Activity Card
 * @version: 1.0.0
 * @date: 2025-06-26
 * @license: MIT
 * @website: https://kokonutui.com
 * @github: https://github.com/kokonut-labs/kokonutui
 *
 * Ajustes do OnCar: os aneis viram "tacometros" alimentados por props (cores por
 * token, ids de gradiente unicos), a legenda mostra atual/alvo e o preenchimento
 * nao anima para quem prefere menos movimento.
 */

import { motion, useReducedMotion } from "motion/react";
import { useId } from "react";
import { cn } from "@/lib/utils";

export interface ActivityData {
  label: string;
  /** 0 a 100 */
  value: number;
  /** Inicio e fim do gradiente do anel (aceita var(--...)). */
  color: string;
  colorEnd: string;
  current: number;
  target: number;
  unit: string;
}

interface CircleProgressProps {
  data: ActivityData;
  index: number;
  size: number;
  strokeWidth: number;
}

const CircleProgress = ({ data, index, size, strokeWidth }: CircleProgressProps) => {
  const reduzido = useReducedMotion();
  const gradientId = useId();
  const radius = (size - strokeWidth) / 2;
  const circumference = radius * 2 * Math.PI;
  const progress = ((100 - Math.min(Math.max(data.value, 0), 100)) / 100) * circumference;

  return (
    <motion.div
      animate={{ opacity: 1, scale: 1 }}
      className="absolute inset-0 flex items-center justify-center"
      initial={reduzido ? false : { opacity: 0, scale: 0.85 }}
      transition={{ duration: 0.8, delay: index * 0.2, ease: "easeOut" }}
    >
      <svg
        aria-hidden="true"
        className="-rotate-90 transform"
        height={size}
        viewBox={`0 0 ${size} ${size}`}
        width={size}
      >
        <defs>
          <linearGradient id={gradientId} x1="0%" x2="100%" y1="0%" y2="100%">
            <stop offset="0%" style={{ stopColor: data.color, stopOpacity: 1 }} />
            <stop offset="100%" style={{ stopColor: data.colorEnd, stopOpacity: 1 }} />
          </linearGradient>
        </defs>

        <circle
          className="text-foreground/[0.07]"
          cx={size / 2}
          cy={size / 2}
          fill="none"
          r={radius}
          stroke="currentColor"
          strokeWidth={strokeWidth}
        />

        <motion.circle
          animate={{ strokeDashoffset: progress }}
          cx={size / 2}
          cy={size / 2}
          fill="none"
          initial={reduzido ? false : { strokeDashoffset: circumference }}
          r={radius}
          stroke={`url(#${gradientId})`}
          strokeDasharray={circumference}
          strokeLinecap="round"
          strokeWidth={strokeWidth}
          style={{ filter: "drop-shadow(0 0 5px rgba(0,0,0,0.25))" }}
          transition={{
            duration: 1.8,
            delay: index * 0.2,
            ease: "easeInOut",
          }}
        />
      </svg>
    </motion.div>
  );
};

const DetailedActivityInfo = ({ activities }: { activities: ActivityData[] }) => {
  const reduzido = useReducedMotion();
  return (
    <motion.dl
      animate={{ opacity: 1, x: 0 }}
      className="flex flex-col gap-5"
      initial={reduzido ? false : { opacity: 0, x: 20 }}
      transition={{ duration: 0.5, delay: 0.3 }}
    >
      {activities.map((activity) => (
        <div className="flex flex-col" key={activity.label}>
          <dt className="flex items-center gap-2 rotulo">
            <span
              aria-hidden="true"
              className="size-2 rounded-full"
              style={{ background: `linear-gradient(135deg, ${activity.color}, ${activity.colorEnd})` }}
            />
            {activity.label}
          </dt>
          <dd className="mt-1 flex items-baseline gap-1.5">
            <span className="fonte-display text-2xl">{activity.value}%</span>
            <span className="num text-xs text-muted-foreground">
              {activity.current}/{activity.target} {activity.unit}
            </span>
          </dd>
        </div>
      ))}
    </motion.dl>
  );
};

export default function AppleActivityCard({
  activities,
  size = 176,
  className,
}: {
  activities: ActivityData[];
  /** Diametro do anel externo; os internos encolhem de 36 em 36px. */
  size?: number;
  className?: string;
}) {
  const strokeWidth = 14;
  const descricao = activities.map((activity) => `${activity.label}: ${activity.value}%`).join(", ");

  return (
    <div className={cn("flex flex-wrap items-center gap-8", className)}>
      <div
        aria-label={descricao}
        className="relative shrink-0"
        role="img"
        style={{ width: size, height: size }}
      >
        {activities.map((activity, index) => (
          <CircleProgress
            data={activity}
            index={index}
            key={activity.label}
            size={size - index * 36}
            strokeWidth={strokeWidth}
          />
        ))}
      </div>
      <DetailedActivityInfo activities={activities} />
    </div>
  );
}
