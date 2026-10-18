export function Availability({ available }: { available: boolean }) {
  return (
    <span className={available ? "availability available" : "availability"}>
      {available ? "Disponível" : "Indisponível no momento"}
    </span>
  );
}
