package appeng.crafting.v2.resolvers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import appeng.api.util.IVirtualItem;
import net.minecraft.world.World;

import org.apache.logging.log4j.Level;

import appeng.api.config.Actionable;
import appeng.api.config.CraftingMode;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.core.AEConfig;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;
import appeng.crafting.v2.CraftingRequest.SubstitutionMode;
import appeng.crafting.v2.CraftingTreeSerializer;
import appeng.crafting.v2.ITreeSerializable;
import appeng.crafting.v2.resolvers.CraftingTask.State;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import io.netty.buffer.ByteBuf;

public class CraftableItemResolver implements CraftingRequestResolver<IAEItemStack> {

    public static class RequestAndPerCraftAmount implements ITreeSerializable {

        public final CraftingRequest<IAEItemStack> request;
        public final long perCraftAmount;

        public RequestAndPerCraftAmount(CraftingRequest<IAEItemStack> request, long perCraftAmount) {
            this.request = request;
            this.perCraftAmount = perCraftAmount;
        }

        @SuppressWarnings({ "unchecked", "unused" })
        public RequestAndPerCraftAmount(CraftingTreeSerializer serializer, ITreeSerializable parent)
                throws IOException {
            this.perCraftAmount = serializer.getBuffer().readLong();
            this.request = new CraftingRequest<>(serializer, parent);
        }

        @Override
        public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
            serializer.getBuffer().writeLong(this.perCraftAmount);
            return request.serializeTree(serializer);
        }

        @Override
        public void loadChildren(List<ITreeSerializable> children) throws IOException {
            request.loadChildren(children);
        }

        @Override
        public ITreeSerializable getSerializationParent() {
            return request;
        }
    }

    public static class CraftFromPatternTask extends CraftingTask<IAEItemStack> {

        public final ICraftingPatternDetails pattern;
        public final boolean allowSimulation;
        public final boolean isComplex;
        // Inputs needed to kickstart recursive crafting
        protected final IAEItemStack[] patternRecursionInputs;
        // With the recursive part subtracted
        protected final IAEItemStack[] patternInputs;
        // With the recursive part subtracted
        protected final IAEItemStack[] patternOutputs;
        protected final IAEItemStack matchingOutput;
        public IAEItemStack craftingMachine;
        protected final ArrayList<RequestAndPerCraftAmount> childRequests = new ArrayList<>();
        protected final ArrayList<CraftingRequest> complexRequestPerSlot = new ArrayList<>();
        protected final Map<IAEItemStack, CraftingRequest<IAEItemStack>> childRecursionRequests = new HashMap<>();
        // byproduct injected -> amount per craft
        protected final IdentityHashMap<IAEItemStack, Long> byproducts = new IdentityHashMap<>();
        protected boolean requestedInputs = false;
        protected long totalCraftsDone = 0, fulfilledAmount = 0;
        /**
         * If matchingOutput's stack size is greater than 1, this keeps track of how many remainder items were injected
         * back into the context.
         */
        protected long matchingOutputRemainderItems = 0;

        public CraftFromPatternTask(CraftingRequest<IAEItemStack> request, ICraftingPatternDetails pattern,
                int priority, boolean allowSimulation, boolean isComplex) {
            super(request, priority);
            this.pattern = pattern;
            this.allowSimulation = allowSimulation;
            this.isComplex = isComplex;

            IAEItemStack[] patternInputs = pattern.getCondensedInputs();
            IAEItemStack[] patternOutputs = pattern.getCondensedOutputs();
            if (!hasRecursiveInputs(patternInputs, patternOutputs)) {
                this.patternInputs = patternInputs;
                this.patternOutputs = patternOutputs;
                this.patternRecursionInputs = new IAEItemStack[0];
            } else {
                patternInputs = Arrays.stream(patternInputs).map(IAEItemStack::copy).toArray(IAEItemStack[]::new);
                patternOutputs = Arrays.stream(patternOutputs).map(IAEItemStack::copy).toArray(IAEItemStack[]::new);

                this.patternRecursionInputs = calculateRecursiveInputs(patternInputs, patternOutputs);
                // Inputs or outputs have been modified, so we need to filter out empty stacks
                this.patternInputs = filterMeaningfulStacks(patternInputs);
                this.patternOutputs = filterMeaningfulStacks(patternOutputs);
            }

            IAEItemStack matchingOutput = null;
            for (IAEItemStack patternOutput : this.patternOutputs) {
                if (isOutputSameAs(patternOutput)) {
                    matchingOutput = patternOutput;
                    break;
                }
            }

            this.matchingOutput = matchingOutput;

            if (matchingOutput == null) {
                state = State.FAILURE;
            }
        }

        @SuppressWarnings("unused")
        public CraftFromPatternTask(CraftingTreeSerializer serializer, ITreeSerializable parent) throws IOException {
            super(serializer, parent);
            final ByteBuf buffer = serializer.getBuffer();

            this.pattern = serializer.readPattern();
            this.allowSimulation = buffer.readBoolean();
            this.isComplex = buffer.readBoolean();
            this.matchingOutput = serializer.readItemStack();
            this.craftingMachine = serializer.readItemStack();
            this.totalCraftsDone = buffer.readLong();

            IAEItemStack[] patternInputs = pattern.getCondensedInputs();
            IAEItemStack[] patternOutputs = pattern.getCondensedOutputs();
            if (!hasRecursiveInputs(patternInputs, patternOutputs)) {
                this.patternInputs = patternInputs;
                this.patternOutputs = patternOutputs;
                this.patternRecursionInputs = new IAEItemStack[0];
            } else {
                patternInputs = Arrays.stream(patternInputs).map(IAEItemStack::copy).toArray(IAEItemStack[]::new);
                patternOutputs = Arrays.stream(patternOutputs).map(IAEItemStack::copy).toArray(IAEItemStack[]::new);

                this.patternRecursionInputs = calculateRecursiveInputs(patternInputs, patternOutputs);
                // Inputs or outputs have been modified, so we need to filter out empty stacks
                this.patternInputs = filterMeaningfulStacks(patternInputs);
                this.patternOutputs = filterMeaningfulStacks(patternOutputs);
            }
        }

        private static IAEItemStack[] calculateRecursiveInputs(IAEItemStack[] pInputs, IAEItemStack[] pOutputs) {
            IAEItemStack[] recInputs = null;
            // While this is a O(n*m) algorithm, no allocations are needed except for the actual output.
            for (IAEItemStack output : pOutputs) {
                for (IAEItemStack input : pInputs) {
                    if (!input.equals(output)) {
                        continue;
                    }

                    final long netProduced = output.getStackSize() - input.getStackSize();

                    IAEItemStack recInput;
                    if (netProduced > 0) {
                        recInput = input.copy();
                        input.setStackSize(0);
                        output.setStackSize(netProduced);
                    } else {
                        // Ensure recInput.stackSize + input.stackSize == original input.stackSize
                        recInput = input.copy().setStackSize(input.getStackSize() + netProduced);
                        input.setStackSize(-netProduced);
                        output.setStackSize(0);
                    }

                    if (!recInput.isMeaningful()) {
                        continue;
                    }

                    if (recInputs == null) {
                        recInputs = new IAEItemStack[] { recInput };
                    } else {
                        recInputs = Arrays.copyOf(recInputs, recInputs.length + 1);
                        recInputs[recInputs.length - 1] = recInput;
                    }
                }
            }

            return recInputs == null ? new IAEItemStack[0] : recInputs;
        }

        private static boolean hasRecursiveInputs(IAEItemStack[] pInputs, IAEItemStack[] pOutputs) {
            for (IAEItemStack output : pOutputs) {
                for (IAEItemStack input : pInputs) {
                    if (input.equals(output)) {
                        return true;
                    }
                }
            }

            return false;
        }

        private static IAEItemStack[] filterMeaningfulStacks(IAEItemStack[] stacks) {
            int i = 0, j = 0;
            for (; i < stacks.length; i++) {
                IAEItemStack stack = stacks[i];
                if (stack.isMeaningful()) {
                    stacks[j] = stack;
                    j++;
                }
            }

            return i == j ? stacks : Arrays.copyOf(stacks, j);
        }

        @Override
        public List<? extends ITreeSerializable> serializeTree(CraftingTreeSerializer serializer) throws IOException {
            super.serializeTree(serializer);
            final ByteBuf buffer = serializer.getBuffer();
            serializer.writePattern(pattern);
            buffer.writeBoolean(allowSimulation);
            buffer.writeBoolean(isComplex);
            serializer.writeItemStack(matchingOutput);
            serializer.writeItemStack(craftingMachine);
            buffer.writeLong(totalCraftsDone);
            return this.childRequests;
        }

        @Override
        public void loadChildren(List<ITreeSerializable> children) throws IOException {
            for (ITreeSerializable child : children) {
                if (!(child instanceof RequestAndPerCraftAmount)) {
                    throw new UnsupportedOperationException(
                            "Invalid craftable request child type: " + child.getClass());
                }
                this.childRequests.add((RequestAndPerCraftAmount) child);
            }
        }

        public List<CraftingRequest<IAEItemStack>> getChildRequests() {
            return childRequests.stream().map(r -> r.request).collect(Collectors.toList());
        }

        public long getTotalCraftsDone() {
            return totalCraftsDone;
        }

        public IAEItemStack getCraftingMachine() {
            return craftingMachine;
        }

        public boolean isOutputSameAs(IAEItemStack otherStack) {
            if (request.substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
                return this.request.stack.fuzzyComparison(otherStack, FuzzyMode.IGNORE_ALL);
            } else {
                return this.request.stack.isSameType(otherStack);
            }
        }

        public boolean isValidSubstitute(IAEItemStack reference, IAEItemStack stack, World world) {
            if (!pattern.isCraftable() && !(stack.getItem() instanceof IVirtualItem)) {
                return true;
            }
            IAEItemStack[] rawInputs = pattern.getInputs();
            for (int slot = 0; slot < rawInputs.length; slot++) {
                if (rawInputs[slot] != null && rawInputs[slot].isSameType(reference)) {
                    return pattern.isValidItemForSlot(slot, stack.getItemStack(), world);
                }
            }
            return true;
        }

        public boolean isValidSubstitute(IAEItemStack reference, IAEItemStack stack, World world, int slot) {
            if (!pattern.isCraftable() && !(stack.getItem() instanceof IVirtualItem)) {
                return true;
            }
            IAEItemStack[] rawInputs = pattern.getInputs();
            return pattern.isValidItemForSlot(slot, stack.getItemStack(), world);
        }

        @Override
        public StepOutput calculateOneStep(CraftingContext context) {
            if (request.remainingToProcess <= 0) {
                state = State.SUCCESS;
                return new StepOutput(Collections.emptyList());
            }
            final boolean canUseSubstitutes = pattern.canSubstitute();
            final SubstitutionMode childMode = canUseSubstitutes ? SubstitutionMode.ACCEPT_FUZZY
                    : SubstitutionMode.PRECISE;
            final long toCraft = Platform
                    .ceilDiv(isComplex ? 1 : request.remainingToProcess, matchingOutput.getStackSize());

            if (requestedInputs) {
                // Calculate how many full recipes we could fulfill
                long maxCraftable = toCraft;
                for (CraftingRequest<IAEItemStack> recInputChild : childRecursionRequests.values()) {
                    if (recInputChild.remainingToProcess > 0) {
                        // If we can't resolve an input to the recursive process, we can't craft anything at all
                        maxCraftable = 0;
                        break;
                    }
                }
                for (RequestAndPerCraftAmount inputChildPair : childRequests) {
                    final CraftingRequest<IAEItemStack> inputChild = inputChildPair.request;
                    final long costPerRecipe = inputChild.stack.getStackSize() / toCraft;
                    final long available = inputChild.stack.getStackSize() - inputChild.remainingToProcess;
                    final long fullRecipes = available / costPerRecipe;
                    maxCraftable = Math.min(maxCraftable, fullRecipes);
                }
                final long producedMatchingOutput = Math.multiplyExact(maxCraftable, matchingOutput.getStackSize());
                this.matchingOutputRemainderItems = (producedMatchingOutput > this.request.remainingToProcess)
                        ? (producedMatchingOutput - this.request.remainingToProcess)
                        : 0;
                this.fulfilledAmount = producedMatchingOutput - matchingOutputRemainderItems;
                // Fulfill those recipes
                request.fulfill(this, matchingOutput.copy().setStackSize(fulfilledAmount), context);
                // Add remainder
                if (matchingOutputRemainderItems > 0) {
                    context.byproductsInventory.injectItems(
                            matchingOutput.copy().setStackSize(matchingOutputRemainderItems),
                            Actionable.MODULATE,
                            context.actionSource);
                }
                // Add byproducts of complex recipes
                if (isComplex && fulfilledAmount > 0) {
                    if (maxCraftable > 1) {
                        throw new IllegalStateException(
                                "Complex recipe got calculated with more than 1 set of inputs at a time");
                    }
                    final IAEItemStack[] inputs = new IAEItemStack[9];
                    for (int slot = 0; slot < complexRequestPerSlot.size(); slot++) {
                        final CraftingRequest<IAEItemStack> slotRequest = complexRequestPerSlot.get(slot);
                        if (slotRequest != null) {
                            final IAEItemStack resolvedItem = (IAEItemStack) slotRequest.getOneResolvedType();
                            inputs[slot] = resolvedItem.copy();
                        }
                    }
                    final IAEItemStack[] leftovers = pattern.canBeEventSended() ? context.simulateComplexCrafting(inputs, pattern) : new IAEItemStack[0];

                    for (IAEItemStack leftover : leftovers) {
                        if (leftover == null || leftover.getStackSize() <= 0) {
                            continue;
                        }
                        context.byproductsInventory.injectItems(leftover, Actionable.MODULATE, context.actionSource);
                        this.byproducts.put(leftover.copy(), leftover.getStackSize());
                    }
                }
                for (IAEItemStack output : patternOutputs) {
                    // add byproducts to the system
                    if (output != matchingOutput) {
                        final IAEItemStack injected = output.copy()
                                .setStackSize(Math.multiplyExact(maxCraftable, output.getStackSize()));
                        context.byproductsInventory.injectItems(injected, Actionable.MODULATE, context.actionSource);
                        this.byproducts.put(injected.copy(), output.getStackSize());
                    }
                }
                this.totalCraftsDone = maxCraftable;
                if (maxCraftable != toCraft) {
                    // Need to refund some items as not everything could be crafted.
                    for (RequestAndPerCraftAmount inputChildPair : childRequests) {
                        final CraftingRequest<IAEItemStack> inputChild = inputChildPair.request;
                        final long actuallyNeeded = Math
                                .multiplyExact(inputChild.stack.getStackSize() / toCraft, maxCraftable);
                        final long produced = inputChild.stack.getStackSize()
                                - Math.max(inputChild.remainingToProcess, 0);
                        if (produced > actuallyNeeded) {
                            if (maxCraftable == 0) {
                                inputChild.fullRefund(context);
                            } else {
                                inputChild.partialRefund(context, produced - actuallyNeeded);
                            }
                        }
                    }
                    // If we couldn't craft even a single recipe, refund recursive inputs too
                    if (maxCraftable == 0) {
                        for (CraftingRequest<IAEItemStack> recChild : childRecursionRequests.values()) {
                            recChild.fullRefund(context);
                        }
                    }
                }
                if (totalCraftsDone > 0) {
                    for (RequestAndPerCraftAmount inputChildPair : childRequests) {
                        if (inputChildPair.request.wasSimulated) {
                            this.request.wasSimulated = true;
                            break;
                        }
                    }
                }
                // Determine an icon for the crafting plan
                this.craftingMachine = context.getCrafterIconForPattern(this.pattern);
                state = State.SUCCESS;
                return new StepOutput(Collections.emptyList());
            } else {
                request.patternParents.add(this.pattern);
                ArrayList<CraftingRequest<IAEItemStack>> newChildren = new ArrayList<>(
                        patternRecursionInputs.length + patternInputs.length);
                if (isComplex) {
                    if (toCraft > 1) {
                        throw new IllegalStateException();
                    }
                    final IAEItemStack[] slotInputs = pattern.getInputs();
                    for (int slot = 0; slot < slotInputs.length; slot++) {
                        final IAEItemStack input = slotInputs[slot];
                        if (input == null) {
                            complexRequestPerSlot.add(null);
                            continue;
                        }
                        final long amount = Math.multiplyExact(input.getStackSize(), toCraft);
                        final int finalSlot = slot; // for lambda capture
                        CraftingRequest<IAEItemStack> req = new CraftingRequest<>(
                                input.copy().setStackSize(amount),
                                childMode,
                                IAEItemStack.class,
                                allowSimulation,
                                request.craftingMode,
                                stack -> this.isValidSubstitute(input, stack, context.world, finalSlot));
                        complexRequestPerSlot.add(req);
                        newChildren.add(req);
                        childRequests.add(new RequestAndPerCraftAmount(req, input.getStackSize()));
                    }
                    // Try to fulfill container items (like GT tools) last to prevent them being frozen while other
                    // ingredients are resolved
                    newChildren.sort(
                            Comparator
                                    .<CraftingRequest<IAEItemStack>>comparingInt(
                                            r -> r.stack.getItem().hasContainerItem(r.stack.getItemStack()) ? 1 : 0)
                                    .thenComparingInt(
                                            r -> r.stack.getItem().getItemStackLimit(r.stack.getItemStack()) == 1 ? 1
                                                    : 0));
                } else {
                    if (patternRecursionInputs.length > 0) {
                        for (IAEItemStack recInput : patternRecursionInputs) {
                            CraftingRequest<IAEItemStack> req = new CraftingRequest<>(
                                    recInput.copy(),
                                    childMode,
                                    IAEItemStack.class,
                                    allowSimulation,
                                    request.craftingMode,
                                    stack -> this.isValidSubstitute(recInput, stack, context.world));
                            newChildren.add(req);
                            childRecursionRequests.put(recInput, req);
                        }
                        state = State.NEEDS_MORE_WORK;
                    }
                    for (IAEItemStack input : patternInputs) {
                        final long amount = Math.multiplyExact(input.getStackSize(), toCraft);
                        CraftingRequest<IAEItemStack> req = new CraftingRequest<>(
                                input.copy().setStackSize(amount),
                                childMode,
                                IAEItemStack.class,
                                allowSimulation,
                                request.craftingMode,
                                stack -> this.isValidSubstitute(input, stack, context.world));
                        newChildren.add(req);
                        childRequests.add(new RequestAndPerCraftAmount(req, input.getStackSize()));
                    }
                }
                childRequests.trimToSize();
                complexRequestPerSlot.trimToSize();
                requestedInputs = true;
                state = State.NEEDS_MORE_WORK;
                return new StepOutput(Collections.unmodifiableList(newChildren));
            }
        }

        @Override
        public long partialRefund(CraftingContext context, long amount) {
            final long oldTotalCrafts = this.totalCraftsDone;
            final long oldTotalMade = this.totalCraftsDone * this.matchingOutput.getStackSize();
            final long oldFulfilled = this.fulfilledAmount;
            final long newFulfilled = oldFulfilled - amount;
            final long newTotalCrafts = Platform.ceilDiv(newFulfilled, this.matchingOutput.getStackSize());
            final long newTotalMade = newTotalCrafts * this.matchingOutput.getStackSize();
            final long oldRemainder = this.matchingOutputRemainderItems;
            final long newRemainder = newTotalMade - newFulfilled;
            if (newRemainder < 0 || newRemainder > this.matchingOutput.getStackSize()) {
                throw new IllegalStateException("Refund remainder invariant broken: " + newRemainder + " - " + this);
            }
            if (newTotalCrafts <= 0) {
                fullRefund(context);
                return amount;
            }
            if (newRemainder != oldRemainder) {
                if (newRemainder > oldRemainder) {
                    context.byproductsInventory.injectItems(
                            matchingOutput.copy().setStackSize(newRemainder - oldRemainder),
                            Actionable.MODULATE,
                            context.actionSource);
                } else {
                    context.byproductsInventory.extractItems(
                            matchingOutput.copy().setStackSize(oldRemainder - newRemainder),
                            Actionable.MODULATE,
                            context.actionSource);
                }
                this.matchingOutputRemainderItems = newRemainder;
            }
            if (newTotalCrafts != oldTotalCrafts) {
                if (newTotalCrafts > oldTotalCrafts) {
                    throw new IllegalStateException(
                            "Refund total crafts invariant broken: " + newTotalCrafts + " - " + this);
                }
                this.totalCraftsDone = newTotalCrafts;
                final long craftsRefunded = oldTotalCrafts - newTotalCrafts;
                for (RequestAndPerCraftAmount subrequest : childRequests) {
                    subrequest.request.partialRefund(context, subrequest.perCraftAmount * craftsRefunded);
                }
                for (Entry<IAEItemStack, Long> entry : byproducts.entrySet()) {
                    final IAEItemStack byproductStack = entry.getKey();
                    final long perCraft = entry.getValue();
                    context.byproductsInventory.extractItems(
                            byproductStack.copy().setStackSize(perCraft * craftsRefunded),
                            Actionable.MODULATE,
                            context.actionSource);
                    entry.getKey().setStackSize(byproductStack.getStackSize() - craftsRefunded * perCraft);
                }
            }
            this.fulfilledAmount = newFulfilled;
            return oldFulfilled - newFulfilled;
        }

        @Override
        public void fullRefund(CraftingContext context) {
            request.patternParents.remove(this.pattern);
            totalCraftsDone = 0;
            fulfilledAmount = 0;
            childRequests.forEach(req -> req.request.fullRefund(context));
            childRequests.clear();
            childRecursionRequests.values().forEach(req -> req.fullRefund(context));
            childRecursionRequests.clear();
            // extract all byproducts because they are no longer produced
            for (IAEItemStack byproduct : byproducts.keySet()) {
                context.byproductsInventory.extractItems(byproduct.copy(), Actionable.MODULATE, context.actionSource);
            }
            byproducts.clear();
            if (this.matchingOutputRemainderItems > 0) {
                context.byproductsInventory.extractItems(
                        matchingOutput.copy().setStackSize(matchingOutputRemainderItems),
                        Actionable.MODULATE,
                        context.actionSource);
                this.matchingOutputRemainderItems = 0;
            }
        }

        @Override
        public void populatePlan(IItemList<IAEItemStack> targetPlan) {
            if (totalCraftsDone == 0) {
                return;
            }
            for (IAEItemStack output : patternOutputs) {
                targetPlan.addRequestable(
                        output.copy().setStackSize(0).setCountRequestable(output.getStackSize() * totalCraftsDone)
                                .setCountRequestableCrafts(totalCraftsDone));
            }
        }

        @Override
        public void startOnCpu(CraftingContext context, CraftingCPUCluster cpuCluster,
                MECraftingInventory craftingInv) {
            cpuCluster.addCrafting(pattern, totalCraftsDone);
        }

        @Override
        public String toString() {
            return "CraftFromPatternTask{" + "request="
                    + request
                    + ", pattern="
                    + pattern
                    + ", allowSimulation="
                    + allowSimulation
                    + ", matchingOutput="
                    + matchingOutput
                    + ", requestedInputs="
                    + requestedInputs
                    + ", totalCraftsDone="
                    + totalCraftsDone
                    + ", priority="
                    + priority
                    + ", state="
                    + state
                    + '}';
        }

        @Override
        public String getTooltipText() {
            return GuiText.Crafting.getLocal() + "\n "
                    + GuiText.Crafts.getLocal()
                    + ": "
                    + totalCraftsDone
                    + "\n "
                    + GuiText.Interface.getLocal()
                    + ": "
                    + Platform.getItemDisplayName(craftingMachine);
        }
    }

    private void logComplexPattrn(ICraftingPatternDetails pattern, long count) {
        if (AEConfig.instance != null && AEConfig.instance.isFeatureEnabled(AEFeature.ComplexPatternLog)) {
            StringBuilder outputs = new StringBuilder();
            for (IAEItemStack stack : pattern.getOutputs()) {
                if (stack != null) {
                    outputs.append(stack);
                    if (stack instanceof AEItemStack) {
                        outputs.append(" <");
                        try {
                            outputs.append(((AEItemStack) stack).getDisplayName());
                        } catch (Exception e) {
                            outputs.append("? " + e.getMessage());
                        }
                        outputs.append('>');
                    }
                    outputs.append(", ");
                }
            }
            AELog.log(Level.INFO, "Complex crafting pattern found: %d * %s", count, outputs);
        }
    }

    @Nonnull
    @Override
    public List<CraftingTask> provideCraftingRequestResolvers(@Nonnull CraftingRequest<IAEItemStack> request,
            @Nonnull CraftingContext context) {
        final ArrayList<CraftingTask> tasks = new ArrayList<>();
        final Set<ICraftingPatternDetails> denyList = request.patternParents;
        final List<ICraftingPatternDetails> patterns = new ArrayList<>(context.getPrecisePatternsFor(request.stack));
        patterns.removeAll(denyList);
        patterns.sort(Comparator.comparing(ICraftingPatternDetails::getPriority).reversed());
        // If fuzzy patterns are allowed,
        if (request.substitutionMode == SubstitutionMode.ACCEPT_FUZZY) {
            final List<ICraftingPatternDetails> fuzzyPatterns = new ArrayList<>(
                    context.getFuzzyPatternsFor(request.stack));
            fuzzyPatterns.removeAll(denyList);
            fuzzyPatterns.sort(Comparator.comparing(ICraftingPatternDetails::getPriority).reversed());
            patterns.addAll(fuzzyPatterns);
        }
        int priority = CraftingTask.PRIORITY_CRAFT_OFFSET + patterns.size() - 1;

        tasks.ensureCapacity(patterns.size() + 1);
        for (ICraftingPatternDetails pattern : patterns) {
            if (context.isPatternComplex(pattern)) {
                logComplexPattrn(pattern, request.remainingToProcess);
                for (int i = 0; i < request.remainingToProcess; i++) {
                    CraftFromPatternTask task = new CraftFromPatternTask(
                            request,
                            pattern,
                            priority,
                            request.craftingMode == CraftingMode.IGNORE_MISSING,
                            true);
                    if (task.getState() != State.FAILURE) {
                        tasks.add(task);
                    }
                }
            } else {
                CraftFromPatternTask task = new CraftFromPatternTask(
                        request,
                        pattern,
                        priority,
                        request.craftingMode == CraftingMode.IGNORE_MISSING,
                        false);
                if (task.getState() != State.FAILURE) {
                    tasks.add(task);
                }
            }
            priority--;
        }
        // Fallback: use highest priority pattern to simulate if nothing else works
        if (!patterns.isEmpty()) {
            ICraftingPatternDetails pattern = patterns.get(0);
            if (context.isPatternComplex(pattern)) {
                for (int i = 0; i < request.remainingToProcess; i++) {
                    CraftFromPatternTask task = new CraftFromPatternTask(request, pattern, priority, true, true);
                    if (task.getState() != State.FAILURE) {
                        tasks.add(task);
                    }
                }
            } else {
                CraftFromPatternTask task = new CraftFromPatternTask(
                        request,
                        pattern,
                        CraftingTask.PRIORITY_SIMULATE_CRAFT,
                        true,
                        false);
                if (task.getState() != State.FAILURE) {
                    tasks.add(task);
                }
            }
        }

        return Collections.unmodifiableList(tasks);
    }
}
