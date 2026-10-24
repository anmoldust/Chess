import { useEffect, useState } from 'react';

export type Route = { name: 'lobby' } | { name: 'game'; id: string };

function parse(hash: string): Route {
  const match = hash.match(/^#\/game\/([^/?#]+)/);
  if (match) {
    return { name: 'game', id: decodeURIComponent(match[1]) };
  }
  return { name: 'lobby' };
}

export function useHashRoute(): Route {
  const [route, setRoute] = useState<Route>(() => parse(window.location.hash));

  useEffect(() => {
    const onChange = () => setRoute(parse(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);

  return route;
}

export function navigate(route: Route): void {
  window.location.hash = route.name === 'game' ? `#/game/${encodeURIComponent(route.id)}` : '#/';
}
