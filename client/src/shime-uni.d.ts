export {}

declare module "vue" {
  type Hooks = App.AppInstance & Page.PageInstance;
  // eslint-disable-next-line @typescript-eslint/no-empty-object-type -- 声明合并必须用 interface（uni-app 官方 shim）
  interface ComponentCustomOptions extends Hooks {}
}