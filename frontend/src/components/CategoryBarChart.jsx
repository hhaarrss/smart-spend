import React from 'react';
import {
  BarChart, Bar, XAxis, YAxis, Tooltip, ResponsiveContainer, Cell, LabelList
} from 'recharts';
import { getCategoryMeta } from '../utils/categoryMeta';

/**
 * Horizontal category spend bars. Bar length is share of total spend.
 */
const CategoryBarChart = ({ categories = [] }) => {
  if (!categories.length) {
    return (
      <div className="h-56 flex items-center justify-center text-xs text-slate-400 font-medium">
        No spending data to chart for this month.
      </div>
    );
  }

  const chartData = categories.map((cat) => ({
    ...cat,
    label: `₹${Number(cat.total).toLocaleString('en-IN')}  ${cat.percentage}%`,
  }));

  const chartHeight = Math.max(220, chartData.length * 44);

  return (
    <div style={{ height: chartHeight }} className="w-full">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart
          layout="vertical"
          data={chartData}
          margin={{ top: 8, right: 120, left: 8, bottom: 8 }}
        >
          <XAxis type="number" domain={[0, 100]} hide />
          <YAxis
            type="category"
            dataKey="category"
            width={118}
            tick={{ fontSize: 11, fill: '#475569', fontWeight: 600 }}
            axisLine={false}
            tickLine={false}
          />
          <Tooltip
            cursor={{ fill: 'rgba(15, 23, 42, 0.04)' }}
            formatter={(_val, _name, item) => [
              `₹${Number(item.payload.total).toLocaleString('en-IN')} (${item.payload.percentage}%)`,
              'Share of spend'
            ]}
          />
          <Bar dataKey="percentage" radius={[0, 8, 8, 0]} barSize={22}>
            {chartData.map((entry) => (
              <Cell key={entry.category} fill={getCategoryMeta(entry.category).color} />
            ))}
            <LabelList
              dataKey="label"
              position="right"
              style={{ fontSize: 11, fill: '#334155', fontWeight: 700 }}
            />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
};

export default CategoryBarChart;
