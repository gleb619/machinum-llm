export function initApp() {
  return {

    init() {
        var objs = Object.getOwnPropertyNames(this);
        for(let i in objs){
          const key = objs[i];

          if (key.startsWith('init') && key !== 'init' && typeof this[key] === 'function') {
            this[key]();
          }

        }
    },

  };
}