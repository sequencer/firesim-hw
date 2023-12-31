init:
	git submodule update --init

patch:
	find patches -type f | awk -F/ '{print("(echo "$$0" && cd dependencies/" $$2 " && git apply -3 --ignore-space-change --ignore-whitespace ../../" $$0 ")")}' | sh

depatch:
	git submodule foreach 'git reset --hard && git clean -fdx'

bump:
	git submodule foreach git stash
	git submodule update --remote
	git add dependencies

update-patches:
	rm -rf patches
	sed '/BEGIN-PATCH/,/END-PATCH/!d;//d' readme.md | awk '{print("mkdir -p patches/" $$1 " && wget " $$2 " -P patches/" $$1 )}' | parallel
	git add patches

bsp:
	mill -i mill.bsp.BSP/install

compile:
	mill -i -j 0 __.compile

clean:
	git clean -fd

out/playground/elaborate.dest/FireSim.fir:
	mill -i -j 0 playground.elaborate

out/playground/elaborate.dest/FireSim-generated.sv: out/playground/elaborate.dest/FireSim.fir
	mill -i -j 0 playground.goldengate
	grep -sh ^ out/playground/elaborate.dest/firrtl_black_box_resource_files.f | xargs cat >> $@
