import { useEffect, useState } from 'react';

export type Route = { name: 'lobby' } | { name: 'game'; id: string } | { name: 'profile' };

function parse(hash: string): Route {
  const match = hash.match(/^#\/game\/([^/?#]+)/);
  if (match) {
    return { name: 'game', id: decodeURIComponent(match[1]) };
  }
  if (/^#\/profile\b/.test(hash)) {
    return { name: 'profile' };
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
  if (route.name === 'game') {
    window.location.hash = `#/game/${encodeURIComponent(route.id)}`;
  } else if (route.name === 'profile') {
    window.location.hash = '#/profile';
  } else {
    window.location.hash = '#/';
  }
}
