# Tidal-syntax-for-SuperCollider
Use Tidal syntax to play with NodeProxy objects.

## The idea

When exploring Tidal i found the short syntax quite nice to use.

This project tries to create that in SClang.

The basic idea is this:

```
NP(~a).snd("bd:2 hh:1 cl:1 sn:7").play(0.7).mon(0.5);
```

The NP class is given a NodeProxy, and has (possibly many) chained methods.  
These methods will operate on the NodeProxy.

NP will initialize the NodeProxy with:
```
proxy.ar(2);
proxy.source = { Silent.ar()!2 };
```

The ```snd()``` method is given a specification what sound(s) to play.  
This specification is parsed and then stored in the NP class.
The specification above specifies 4 steps in a sequence.

Each step starts with a string, and may have extra characters followed by numbers. These extra characters are ```:/*@```.

## The Samples class

NP works together with a Samples class. With this class, you can load samples into memory, and then these can be played by NP.  

```Samples.load(<path>, <ext>)``` loads samples. You specify the ```<path>``` relative to the currently loaded file in SCLang. With ```<ext>``` you can let Samples find all ".wav" or ".aiff" files. But not both at the same time (yet).

Samples expects each subfolder of the specified path folder to contain a bunch of sample files (stereo). The _name_ of the subfolder is what you specify in the string part of a step in the mini-notation.  
If there is a ```:<number>``` part after this name, then this means that the step wants to use the number-th sample in the subfolder. Numbering starts with 0 (the default number) and wraps around.

Samples can load multiple paths this way.

Given the name of a step, NP will first ask Samples if it has a sample with that name. If so then NP uses built-in ```np_playbuf``` synthdef to play it. If not so, then NP will play a synthdef with that name. The number, then, will be interpreted as a ```degree``` (if lower that 20) or as a ```midinote```.

## The 'structure' of the cycle

```
NP(~a).snd("bd:2 hh:1 cl:1 sn:7").play(0.7).mon(0.5);
```
The structure / timing for the Pbind can be derived from any given specification. The specification above shows us 4 steps (notes) to play, so the resulting durations should be ```[1/4, 1/4, 1/4, 1/4]``` used in a Pseq in the Pbind.

We can also specify the numbers separately with the ```num()``` function.

```
NP(~a).snd("bd:5 hh cl sn").num("2 1 1 7").play(0.7).mon(0.5);
```

For the first step, "bd" sample number 2 will be played (5 is ignored).

In NP, each method has two versions: one regular, and one postfixed with an ```_``` character.   
The rule is that the _first_ method call that takes a specification will determine the structure for the Pbind, but the _last_ method call ```xxx_``` method that takes a specification will override that.

The ```param()``` method lets you specify values for any other parameter for the played synthdef (np_playbuf or one of your own synthdefs). This method call could also be the one that determines the structure.

```
NP(~a).snd("bd:5 hh:1 cl:2 sn:7").param_(\amp, "0.2 1 0.7").play(0.7).mon(0.5);
```
This would play accents with a triplet feel, while the 4 sample steps wrap around.

## Rests

As in Tidal, the ```"~"``` string denotes a rest. You can use this string inside the ```snd()``` or ```num()``` specification to create a rest.

## Nesting, alternating

NP supports nested steps using ```[ ]``` and alternating steps using ```< >```.

```
NP(~a).snd("bd <sn:2 [sn:4 bd:2]> ~").play(0.5).mon(0.3);
```
Snaredrum 2 is alternated with a snaredum 4/bassdrum 2 group (playing in double tempo).

## Play()

The ```play()``` method will create a Pbind on the NodeProxy, using the information that has been stored inside the NP object by other methods that were called before ```play()``` was called.

The parameter to ```play()``` is the volume with which the Pbind should play on the NodeProxy private bus.

The default slot index where the Pbind is added is slot 10, but you may specify another slot number. This way you could play more than 1 Pbind at the same time on one NodeProxy.

## Mon()

The ```mon()``` method will call ```play``` on the NodeProxy, and the parameter to ```mon()``` is the monitor volume to use. You could also play the NodeProxy directly, instead of through the NP class (```~a.vol_(0.5).play```).

## Installation

Use ```git clone``` to get the sources somewhere on disk.

Create a symbolic link inside the ```~/.local/share/SuperCollider/Extensions``` folder, pointing to the place where you have cloned this repository to.
Like this: ```ln -s somehwere-on-disk np```. In Extensions folder, you will now have a symbolic link named "np" pointing to the contents of the repository.

Start SuperCollider (this will recompile all classes) and the NP class should be available. SuperCollider will follow the symbolic link.

This is how you can install it on Linux. Without using symbolic links, you can of course just copy all ```*.sc``` files of this repo into Extensions directly.

## Example

Create a ```.scd``` file with this code in it:  
(i assume a "samples" folder with "bd", "sn" etc subfolders with samples)

```
s.boot;

Samples.load("samples", "wav");

p = ProxySpace.push(s).makeTempoClock(100/60).quant_(2);

NP(~a).snd("bd:2 [sn:1 sn:1] ~ bd:2").play(0.5).mon(0.3);

NP(~b).snd("~ ~ [hh hh hh] ~").play(0.5).mon(0.3);

p.clock.tempo_(60/60)
```

Evaluate this line by line in SuperCollider.

## Example setting the structure and using param

```
s.boot;

Samples.load("samples", "wav");

p = ProxySpace.push(s).makeTempoClock(60/60).quant_(2);

NP(~a).snd("bd <~ [~ bd:2]> ~ ~ sn ~ ~ ~").play(0.5).mon(0.3);

NP(~b).snd("~ ~ hh hh").param_(\amp, "0.2 0.8 0.2").play(0.5).mon(0.4);

NP(~c).snd("rd").num_("1 1 1 3").play(0.5).mon(0.1);
```

## Future plans

```bin("10001010")```

```bin()``` could be used to declare a structure, thereby overriding all other structure defining method calls.

```hex("92")```

Just like ```bin()``` you can think of much shorter hexadecimal notation. ```"92"``` in hex equals binary ```"10010010"```.

## Working on:

```,``` paralell running steps

```!``` repeat the last step, e.g.: ```"bd bd bd" = "bd bd !" = "bd!3"```

```bd(3,8)``` euclidian rhythms ( ```bd:2(3,8)``` is also possible )

## Progress:

20211128: parsing ```*/@``` works (stretching)  
20211201: ~ in notes is also a \rest; shortened Pbind;  
20211210: using pbindf, add any other param for your synthdef:  

```
(
NP(~rhythm)
.num("2 0 [1 5] <8 ~ [5 4] 2>")
.snd("potsandpans")
.param(\amp, "0.3 0.2 1 0.3 0.6")
.param(\pan, "-1 0 0 0 1")
.param(\spread, "0")
.play(0.3)
.mon(0.5);
)
```

20211211: the ```*/@``` (stretching) works for value steps now  

```
NP(~a).snd("bd <~ [~ bd:2/1.5]> ~ ~ sn*3/2 ~ ~ ~").play(0.5).mon(0.3);
```

20211221: stretching with ```*/@``` works for ```[]``` and ```<>``` too:

```
NP(~bdsn).snd("bd ~ ~ ~").play(0.5).mon(0.7);
NP(~hh).snd("~ ~ ~ hh@0.4/2").play(0.5).mon(0.7);
NP(~cl).snd("~ <sn:6 ~> [sn:6 sn:5 <ch sn:7>/5]/3 ~").play(0.5).mon(0.7);
```

