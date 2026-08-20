import React, { useEffect, useRef, useState } from 'react';
import { Calendar } from 'lucide-react';

const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December'
];

const YEARS = Array.from({ length: 11 }, (_, i) => 2020 + i);

/**
 * Month/year calendar picker triggered via Calendar icon button.
 */
const MonthPicker = ({ month, year, onChange }) => {
  const [open, setOpen] = useState(false);
  const [pickerYear, setPickerYear] = useState(year);
  const rootRef = useRef(null);

  useEffect(() => {
    setPickerYear(year);
  }, [year]);

  useEffect(() => {
    const onDocClick = (e) => {
      if (rootRef.current && !rootRef.current.contains(e.target)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, []);

  const label = `${MONTHS[month - 1]} ${year}`;

  return (
    <div ref={rootRef} className="relative inline-block">
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        className="flex items-center gap-2 px-3.5 py-2 rounded-xl bg-white border border-slate-200 text-slate-800 hover:border-[#16803C] hover:bg-slate-50 shadow-xs transition-all cursor-pointer"
        aria-label="Open month calendar picker"
      >
        <Calendar className="w-4 h-4 text-[#16803C]" />
        <span className="text-sm font-extrabold tracking-tight">{label}</span>
      </button>

      {open && (
        <div className="absolute top-full right-0 mt-2 z-30 w-72 bg-white border border-slate-200 rounded-2xl shadow-xl p-4">
          <div className="flex items-center justify-between mb-3">
            <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500">Select Month &amp; Year</span>
            <select
              value={pickerYear}
              onChange={(e) => setPickerYear(Number(e.target.value))}
              className="text-xs font-bold bg-slate-50 border border-slate-200 rounded-lg px-2.5 py-1 focus:outline-none focus:border-[#16803C]"
            >
              {YEARS.map((y) => (
                <option key={y} value={y}>{y}</option>
              ))}
            </select>
          </div>
          <div className="grid grid-cols-3 gap-1.5">
            {MONTHS.map((name, idx) => {
              const m = idx + 1;
              const isActive = m === month && pickerYear === year;
              return (
                <button
                  key={name}
                  type="button"
                  onClick={() => {
                    onChange(m, pickerYear);
                    setOpen(false);
                  }}
                  className={`px-2 py-2 rounded-xl text-xs font-bold transition-colors cursor-pointer ${
                    isActive
                      ? 'bg-[#16803C] text-white shadow-xs'
                      : 'text-slate-600 hover:bg-slate-100'
                  }`}
                >
                  {name.slice(0, 3)}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};

export default MonthPicker;
