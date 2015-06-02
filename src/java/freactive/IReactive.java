package freactive;

import clojure.lang.*;

public interface IReactive {
    public final static Var REGISTER_DEP =
            Var.intern(Namespace.findOrCreate(Symbol.intern("freactive.core")),
                    Symbol.intern("*register-dep*"), null, false).setDynamic();

    public class BindingInfo {
        private final IFn deref;
        private final IFn addWatch;
        private final IFn removeWatch;
        private final IFn clean;

        public IFn getDeref() {
            return deref;
        }

        public IFn getAddWatch() {
            return addWatch;
        }

        public IFn getRemoveWatch() {
            return removeWatch;
        }

        public IFn getClean() {
            return clean;
        }

        public BindingInfo(IFn deref, IFn addWatch, IFn removeWatch, IFn clean) {
            this.deref = deref;
            this.addWatch = addWatch;
            this.removeWatch = removeWatch;
            this.clean = clean;
        }
    }

    public static final IReactive.BindingInfo IRefBindingInfo =
            new BindingInfo(new AFn() {
                @Override
                public Object invoke(Object self) {
                    return ((IDeref) self).deref();
                }
            }, new AFn() {
                @Override
                public Object invoke(Object self, Object key, Object f) {
                    return ((IRef) self).addWatch(key, (IFn)f);
                }
            }, new AFn() {
                @Override
                public Object invoke(Object self, Object key) {
                    return ((IRef) self).removeWatch(key);
                }
            }, null);

    public static final IReactive.BindingInfo IInvalidatesBindingInfo =
            new BindingInfo(new AFn() {
                @Override
                public Object invoke(Object self) {
                    return ((IDeref) self).deref();
                }
            }, new AFn() {
                @Override
                public Object invoke(Object self, Object key, Object f) {
                    return ((IInvalidates) self).addInvalidationWatch(key, (IFn)f);
                }
            }, new AFn() {
                @Override
                public Object invoke(Object self, Object key) {
                    return ((IInvalidates) self).removeInvalidationWatch(key);
                }
            }, null);

    BindingInfo getBindingInfo();
}
