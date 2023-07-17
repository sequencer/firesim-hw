CONFIG ?= firesim.firesim:FireSimRocket4GiBDRAMConfig
PLATFORM_CONFIG ?= BaseXilinxVCU118Config

init:
	git submodule update --init

patch:
	find custom-patches -type f | awk -F/ '{print("(echo "$$0" && cd dependencies/" $$2 " && git apply -3 --ignore-space-change --ignore-whitespace ../../" $$0 ")")}' | sh
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

rtl/firesim.fir:
	mkdir -p rtl
	mill -i -j 0 playground.generator --target-dir ./rtl --name firesim --top-module firesim.firesim.FireSim --legacy-configs $(CONFIG)

rtl/FireSim-generated.sv: rtl/firesim.fir
	mill -i -j 0 playground.goldengate -i rtl/firesim.fir -td ./rtl -faf ./rtl/firesim.anno.json -ggcp firesim.firesim -ggcs $(PLATFORM_CONFIG) --output-filename-base FireSim-generated --no-dedup
	grep -sh ^ rtl/firrtl_black_box_resource_files.f | xargs cat >> $@
