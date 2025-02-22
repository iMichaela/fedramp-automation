import { derived, Statemachine, statemachine } from 'overmind';

import * as router from './router';

type States = {
  current: 'VALID_PAGE';
};

type BaseState = {
  currentRoute: router.Route;
  breadcrumbs: { text: string; linkUrl: string | false }[];
};

type Events = {
  type: 'ROUTE_CHANGED';
  data: {
    route: router.Route;
  };
};

export type RouterMachine = Statemachine<States, Events, BaseState>;

export const routerMachine = statemachine<States, Events, BaseState>({
  VALID_PAGE: {
    ROUTE_CHANGED: ({ route }) => {
      return {
        current: 'VALID_PAGE',
        currentRoute: route,
      };
    },
  },
});

export const createRouterMachine = () => {
  return routerMachine.create(
    { current: 'VALID_PAGE' },
    {
      currentRoute: router.Routes.home,
      breadcrumbs: derived((state: BaseState) => {
        const what = router.breadcrumbs[state.currentRoute.type];
        const test = what(state.currentRoute);
        return test;
      }),
    },
  );
};
